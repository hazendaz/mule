/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.deployment.model.api.application;

import org.mule.api.annotation.NoImplement;
import org.mule.runtime.core.api.data.sample.SampleDataService;
import org.mule.runtime.deployment.model.api.DeployableArtifact;
import org.mule.runtime.deployment.model.api.domain.Domain;
import org.mule.runtime.module.artifact.api.classloader.RegionOwnerArtifact;

@NoImplement
public interface Application extends DeployableArtifact<ApplicationDescriptor>, RegionOwnerArtifact {

  /**
   * @return the domain associated with the application.
   */
  Domain getDomain();

  /**
   * @return the current status of the application
   */
  ApplicationStatus getStatus();

  /**
   * @return the policy manager for the application
   */
  ApplicationPolicyManager getPolicyManager();

  /**
   * Do not use this method if the artifact initialization wasn't successful or the application has been destroyed.
   *
   * @return the {@link SampleDataService} which can resolve possible values for a component configuration.
   */
  SampleDataService getSampleDataService();

}
