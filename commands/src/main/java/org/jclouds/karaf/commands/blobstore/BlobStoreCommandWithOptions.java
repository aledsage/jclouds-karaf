/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jclouds.karaf.commands.blobstore;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import org.apache.felix.gogo.commands.Option;
import org.apache.felix.service.command.CommandSession;
import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.karaf.utils.EnvHelper;
import org.jclouds.karaf.utils.ServiceHelper;
import org.jclouds.logging.log4j.config.Log4JLoggingModule;

import java.io.IOException;
import java.io.File;
import java.util.Collections;
import java.util.List;

public abstract class BlobStoreCommandWithOptions extends BlobStoreCommandBase {

   @Option(name = "--name", description = "The service context name. Used to distinct between multiple service of the same provider/api. Only usable in interactive mode.")
   protected String name;

   @Option(name = "--provider", description = "The provider to use.")
   protected String provider;

   @Option(name = "--api", description = "The api to use.")
   protected String api;

   @Option(name = "--identity", description = "The identity to use for creating a blob store.")
   protected String identity;

   @Option(name = "--credential", description = "The credential to use for a blob store.")
   protected String credential;

   @Option(name = "--endpoint", description = "The endpoint to use for a blob store.")
   protected String endpoint;

   @Option(name = "--properties", description = "File with properties.")
   protected String propertiesFile;

   @Override
   public Object execute(CommandSession session) throws Exception {
      this.session = session;
      return doExecute();
   }

   protected List<BlobStore> getBlobStoreServices() {
      if (provider == null && api == null) {
         return blobStoreServices;
      } else {
         try {
            return Collections.singletonList(getBlobStore());
         } catch (Throwable t) {
            return Collections.emptyList();
         }
      }
   }

   protected BlobStore getBlobStore() throws IOException {
      if ((name == null && provider == null && api == null) && (blobStoreServices != null && blobStoreServices.size() == 1)) {
         return blobStoreServices.get(0);
      }

      BlobStore blobStore = null;
      if (propertiesFile != null) {
         EnvHelper.loadProperties(new File(propertiesFile));
      }
      String providerValue = EnvHelper.getBlobStoreProvider(provider);
      String apiValue = EnvHelper.getBlobStoreApi(api);
      String identityValue = EnvHelper.getBlobStoreIdentity(identity);
      String credentialValue = EnvHelper.getBlobStoreCredential(credential);
      if (providerValue.equals("google-cloud-storage")) {
         credentialValue = EnvHelper.getGoogleCredentialFromJsonFile(credentialValue);
      }
      String endpointValue = EnvHelper.getBlobStoreEndpoint(endpoint);
      boolean contextNameProvided = !Strings.isNullOrEmpty(name);
      boolean canCreateService = (!Strings.isNullOrEmpty(providerValue) || !Strings.isNullOrEmpty(apiValue))
               && !Strings.isNullOrEmpty(identityValue) && !Strings.isNullOrEmpty(credentialValue);

      String providerOrApiValue = !Strings.isNullOrEmpty(providerValue) ? providerValue : apiValue;

      try {
         blobStore = ServiceHelper.getService(name, providerOrApiValue, blobStoreServices);
      } catch (Throwable t) {
        if (contextNameProvided) {
          throw new RuntimeException("Could not find blobstore service with id:" + name);
        } else if (!canCreateService) {
            StringBuilder sb = new StringBuilder();
            sb.append("Insufficient information to create blobstore service:\n");
            if (providerOrApiValue == null) {
               sb.append("Missing provider or api." +
                     " Please specify the --provider / --api" +
                     " options, set the " + Constants.PROPERTY_PROVIDER +
                     " / " + Constants.PROPERTY_API + " properties" +
                     ", or set the JCLOUDS_BLOBSTORE_PROVIDER /" +
                     " JCLOUDS_BLOBSTORE_API environment variables.\n");
            }
            if (identityValue == null) {
               sb.append("Missing identity." +
                     " Please specify the --identity option" +
                     ", set the " + Constants.PROPERTY_IDENTITY +
                     " property, or set the " +
                     EnvHelper.JCLOUDS_BLOBSTORE_IDENTITY +
                     " environment variable.\n");
            }
            if (credentialValue == null) {
               sb.append("Missing credential." +
                     " Please specify the --credential option" +
                     ", set the " + Constants.PROPERTY_CREDENTIAL +
                     " property, or set the " +
                     EnvHelper.JCLOUDS_BLOBSTORE_CREDENTIAL +
                     " environment variable.\n");
            }
            throw new RuntimeException(sb.toString());
         }
      }
      if (blobStore == null && canCreateService) {
         try {
            ContextBuilder builder = ContextBuilder.newBuilder(providerOrApiValue)
                     .credentials(identityValue, credentialValue)
                     .modules(ImmutableSet.<Module> of(new Log4JLoggingModule()));
            if (!Strings.isNullOrEmpty(endpointValue)) {
               builder = builder.endpoint(endpointValue);
            }
            BlobStoreContext context = builder.build(BlobStoreContext.class);
            blobStore = context.getBlobStore();
         } catch (Exception ex) {
            throw new RuntimeException("Failed to create service: " + ex.getMessage(), ex);
         }
      }
      return blobStore;
   }
}
