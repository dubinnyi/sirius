/*
 *  This file is part of the SIRIUS Software for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman, Fleming Kretschmer, Marvin Meusel and Sebastian Böcker,
 *  Chair of Bioinformatics, Friedrich-Schilller University.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Affero General Public License
 *  as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>
 */

package de.unijena.bioinf.ms.gui.errorReport;
/**
 * Created by Markus Fleischauer (markus.fleischauer@gmail.com)
 * as part of the sirius_frontend
 * 29.09.16.
 */

import de.unijena.bioinf.fingerid.utils.FingerIDProperties;
import de.unijena.bioinf.ms.frontend.core.ApplicationCore;
import de.unijena.bioinf.utils.errorReport.ErrorReport;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

/**
 * @author Markus Fleischauer (markus.fleischauer@gmail.com)
 */
public class SiriusDefaultErrorReport extends ErrorReport {

    public SiriusDefaultErrorReport(String subject, String userMessage, String userEmail, boolean sendSystemInfo) {
        super(subject);
        setUserMessage(userMessage);
        setUserEmail(userEmail);
        setVersion(FingerIDProperties.sirius_guiVersion());

        File f = null;
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(ApplicationCore.WORKSPACE.resolve("sirius.properties").toFile()));
            prop.setProperty("de.unijena.bioinf.sirius.proxy.user", "CLEANED");
            prop.setProperty("de.unijena.bioinf.sirius.proxy.pw", "CLEANED");
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            prop.store(stream, "This is the cleaned (no passwords and usernames) version of the Properties File");
            ByteArrayInputStream in = new ByteArrayInputStream(stream.toByteArray());
            addAdditionalFiles(in, "sirius.properties");

            f = ApplicationCore.WORKSPACE.resolve("logging.properties").toFile();
            addAdditionalFiles(f);
        } catch (IOException e) {
            LoggerFactory.getLogger(this.getClass()).error("Could not load file: " + f.getAbsolutePath(), e);
        }
        try {
            addAdditionalFiles(ErrorUtils.getErrorLoggingStream(), "sirius.log");
        } catch (Exception e) {
            LoggerFactory.getLogger(this.getClass()).error("Could not load Logging Stream", e);
        }

        //create system info
        if (sendSystemInfo) {
            try {
                addSystemInfoFile();
            } catch (IOException e) {
                LoggerFactory.getLogger(this.getClass()).error("Could not create System info file", e);
            }
        }
    }

    //this constructor is just for deserialisation
    public SiriusDefaultErrorReport() {
        super();

    }
}


