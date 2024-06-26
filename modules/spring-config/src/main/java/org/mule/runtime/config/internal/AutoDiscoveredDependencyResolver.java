/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal;

import static java.util.stream.Collectors.toList;

import java.util.List;
import javax.inject.Inject;

import org.springframework.beans.factory.config.BeanDefinition;

/**
 * Provides the dependencies that are explicit on the {@link BeanDefinition}. These were inferred from introspecting fields
 * annotated with {@link Inject} or were programmatically added to the definition
 */
public class AutoDiscoveredDependencyResolver {

  private SpringRegistry springRegistry;

  public AutoDiscoveredDependencyResolver(SpringRegistry springRegistry) {
    this.springRegistry = springRegistry;
  }

  public List<BeanWrapper> getAutoDiscoveredDependencies(String beanName) {
    return springRegistry.getDependencies(beanName).entrySet()
        .stream()
        .map(x -> new BeanWrapper(x.getKey(), x.getValue()))
        .collect(toList());
  }


}
