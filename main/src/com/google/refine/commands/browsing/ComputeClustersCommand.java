/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.google.refine.commands.browsing;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.browsing.Engine;
import com.google.refine.clustering.Clusterer;
import com.google.refine.clustering.ClustererConfig;
import com.google.refine.clustering.binning.KeyerFactory;
import com.google.refine.clustering.binning.UserDefinedKeyer;
import com.google.refine.clustering.knn.DistanceFactory;
import com.google.refine.clustering.knn.UserDefinedDistance;
import com.google.refine.commands.Command;
import com.google.refine.model.Project;
import com.google.refine.util.ParsingUtilities;

public class ComputeClustersCommand extends Command {

    final static Logger logger = LoggerFactory.getLogger("compute-clusters_command");

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // This command triggers evaluation expression and therefore requires CSRF-protection
        if (!hasValidCSRFToken(request)) {
            respondCSRFError(response);
            return;
        }

        try {
            long start = System.currentTimeMillis();
            Project project = getProject(request);
            Engine engine = getEngine(request, project);
            String clusterer_conf = request.getParameter("clusterer");

            JsonNode jsonObject = ParsingUtilities.mapper.readTree(clusterer_conf);
            JsonNode params = jsonObject.get("params");

            if (params != null && params.has("expression")) {
                String expression = params.get("expression").asText();
                if (jsonObject.has("function") && "UserDefinedKeyer".equals(jsonObject.get("function").asText())) {
                    KeyerFactory.put("userdefinedkeyer", new UserDefinedKeyer(expression));
                } else {
                    DistanceFactory.put("userdefineddistance", new UserDefinedDistance(expression));
                }
            }

            ClustererConfig clustererConfig = ParsingUtilities.mapper.readValue(clusterer_conf, ClustererConfig.class);

            Clusterer clusterer = clustererConfig.apply(project);

            clusterer.computeClusters(engine);

            KeyerFactory.remove("userdefinedkeyer");
            DistanceFactory.remove("userdefineddistance");

            respondJSON(response, clusterer);
            logger.info("computed clusters [{}] in {}ms",
                    new Object[] { clustererConfig.getType(), Long.toString(System.currentTimeMillis() - start) });
        } catch (Exception e) {
            respondException(response, e);
        }
    }
}
