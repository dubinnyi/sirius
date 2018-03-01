package de.unijena.bioinf.ms.cli;

import de.unijena.bioinf.ChemistryBase.algorithm.Scored;
import de.unijena.bioinf.ChemistryBase.fp.ProbabilityFingerprint;
import de.unijena.bioinf.ChemistryBase.jobs.SiriusJobs;
import de.unijena.bioinf.ChemistryBase.properties.PropertyManager;
import de.unijena.bioinf.chemdb.FingerprintCandidate;
import de.unijena.bioinf.fingerid.FingerIdResult;
import de.unijena.bioinf.fingerid.jjobs.FingerIDJJob;
import de.unijena.bioinf.jjobs.BasicJJob;
import de.unijena.bioinf.jjobs.BufferedJJobSubmitter;
import de.unijena.bioinf.jjobs.JobManager;
import de.unijena.bioinf.sirius.IdentificationResult;
import de.unijena.bioinf.sirius.Sirius;
import de.unijena.bioinf.sirius.projectspace.ExperimentResult;
import de.unijena.bioinf.sirius.projectspace.ExperimentResultJJob;
import de.unijena.bioinf.sirius.projectspace.ProjectWriter;
import de.unijena.bioinf.sirius.projectspace.ProjectWriterJJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class FingerIdWorkflow implements Workflow<Instance> {

    FingerIdOptions options;
    ProjectWriter projectWriter;
    SiriusInstanceProcessor siriusInstanceProcessor;
    FingerIdInstanceProcessor fingerIdInstanceProcessor;

    protected Logger logger = LoggerFactory.getLogger(FingerIdWorkflow.class);

    public FingerIdWorkflow(SiriusInstanceProcessor siriusIP, FingerIdInstanceProcessor fingeridIP, FingerIdOptions options, ProjectWriter projectWriter) {
        this.options = options;
        this.projectWriter = projectWriter;
        siriusInstanceProcessor = siriusIP;
        fingerIdInstanceProcessor = fingeridIP;
    }

    @Override
    public boolean setup() {
        if (!siriusInstanceProcessor.setup()) return false;
        if (!fingerIdInstanceProcessor.setup()) return false;
        return true;
    }

    @Override
    public boolean validate() {
        if (!siriusInstanceProcessor.validate()) return false;
        if (!fingerIdInstanceProcessor.validate()) return false;
        return true;
    }


    @Override
    public void compute(Iterator<Instance> allInstances) {
        //set options todo: i would like to do this in the cli parser but how with jewelcli?
        int initBuffer = options.getMinInstanceBuffer() != null ? options.getMinInstanceBuffer() : PropertyManager.getNumberOfCores() * 2;
        int maxBuffer = options.getMaxInstanceBuffer() != null ? options.getMaxInstanceBuffer() : initBuffer * 2;

        if (initBuffer <= 0) {
            initBuffer = Integer.MAX_VALUE; //no buffering, submit all jobs at once
            maxBuffer = 0;
        }

        JobSubmitter submitter = new JobSubmitter(allInstances);
        submitter.start(initBuffer, maxBuffer);

    }


    protected ExperimentResult handleJobs(BufferedJJobSubmitter<Instance>.JobContainer jc) throws IOException {
        //todo add a getJobByIntanceOf method?!
        //sirius
        ExperimentResultJJob j = jc.getJob(SiriusInstanceProcessor.ExperimentResultForSiriusJJob.class);
        logger.info("Sirius results for: '" + jc.sourceInstance.file.getName() + "', " + jc.sourceInstance.experiment.getName());
        ExperimentResult experimentResult = null;
        if (j != null){
//            handleSiriusResults(jc, j); //handle results
            try {
                experimentResult = j.takeResult();
            } catch (RuntimeException e) {
                //cannot happen!?
                logger.debug("Error during computation of " + j.getExperiment().getName(), e);
                experimentResult = new ExperimentResult(j.getExperiment(), null, ExperimentResult.ErrorCause.ERROR, e.getMessage());
            }

            if (experimentResult!=null){
                siriusInstanceProcessor.output(experimentResult);
            } else {
                logger.debug("Null job occurred!");
            }
        }
        else {
            logger.error("Could not load results for " + jc.sourceInstance.file.getName());
        }

        //fingerid
        //todo never null if sirius job not null?!? or might be null because of db problems?
        FingerIDJJob fij = jc.getJob(FingerIDJJob.class);
        if (fij != null) {
            try {
                Map<IdentificationResult, ProbabilityFingerprint> propPrints = fij.awaitResult();
                fingerIdInstanceProcessor.output(propPrints);

                //todo necessary to test!?
                if (experimentResult!=null && experimentResult.getResults()!=null){
                    //add additional IdentificationResult created by FingerIDJob
                    final List<IdentificationResult> total = new ArrayList<>(experimentResult.getResults());
                    fij.takeResult();
                    total.addAll(fij.getAddedIdentificationResults());
                    experimentResult = new ExperimentResult(experimentResult.getExperiment(), total);
                }

            } catch (ExecutionException e) {
                String name = jc.sourceInstance.experiment.getName();
                File file = jc.sourceInstance.file;
                logger.error("Error while searching structure for " + name + " (" + file + "): " + e.getMessage(), e);
            }
        }

        if (experimentResult!=null) writeResults(experimentResult);
        return experimentResult;
    }

    protected void writeResults(ExperimentResult experimentResult) throws IOException {
        if (projectWriter != null) {
            //not thread-safe
//            writerJJobs.add(SiriusJobs.getGlobalJobManager().submitJob(new ProjectWriterJJob(projectWriter, experimentResult)));
            projectWriter.writeExperiment(experimentResult);
        }
    }


    protected static final class CandidateElement extends Scored<FingerprintCandidate> {
        protected final FingerIdResult origin;

        public CandidateElement(FingerIdResult ir, Scored<FingerprintCandidate> c) {
            super(c.getCandidate(), c.getScore());
            this.origin = ir;
        }
    }


    protected class JobSubmitter extends BufferedJJobSubmitter<Instance> {

        public JobSubmitter(Iterator<Instance> instances) {
            super(instances);
        }

        @Override
        protected void submitJobs(final JobContainer watcher) {
            Instance instance = watcher.sourceInstance;
            ExperimentResultJJob siriusJob = siriusInstanceProcessor.makeSiriusJob(instance);
            submitJob(siriusJob, watcher);
            if (options.isFingerid()){
                FingerIDJJob fingerIDJob = fingerIdInstanceProcessor.makeFingerIdJob(instance, siriusJob);
                if (fingerIDJob!=null)
                    submitJob(fingerIDJob, watcher);
            }
        }

        @Override
        protected void handleResults(JobContainer watcher) {
            try {
                handleJobs(watcher);
            } catch (IOException e) {
                logger.error("Error processing instance: " + watcher.sourceInstance.file.getName());
            }
        }

        @Override
        protected JobManager jobManager() {
            return SiriusJobs.getGlobalJobManager();
        }
    }

//    protected class JobSubmitter extends BufferedJJobSubmitter<Instance> implements Iterator<ExperimentResult> {
//
//        public JobSubmitter(Iterator<Instance> instances) {
//            super(instances);
//        }
//
//        @Override
//        protected void submitJobs(final JobContainer watcher) {
//            Instance instance = watcher.sourceInstance;
//            ExperimentResultJJob siriusJob = siriusInstanceProcessor.makeSiriusJob(instance);
//            submitJob(siriusJob, watcher);
//            if (options.isFingerid()){
//                FingerIDJJob fingerIDJob = fingerIdInstanceProcessor.makeFingerIdJob(instance, siriusJob);
//                if (fingerIDJob!=null)
//                    submitJob(fingerIDJob, watcher);
//            }
//        }
//
//        @Override
//        protected void handleResults(JobContainer watcher) {
//            try {
//                handleJobs(watcher);
//            } catch (IOException e) {
//                logger.error("Error processing instance: " + watcher.sourceInstance.file.getName());
//            }
//        }
//
//        @Override
//        protected JobManager jobManager() {
//            return SiriusJobs.getGlobalJobManager();
//        }
//
//        @Override
//        public boolean hasNext() {
//            return false;
//        }
//
//        @Override
//        public ExperimentResult next() {
//            return null;
//        }
//
//        @Override
//        public void remove() {
//            throw new UnsupportedOperationException();
//        }
//    }

}
