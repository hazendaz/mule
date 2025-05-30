/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.functional.junit4;

import static org.mule.runtime.core.internal.config.bootstrap.ClassLoaderRegistryBootstrapDiscoverer.BOOTSTRAP_PROPERTIES;

import static java.util.Collections.emptyList;
import static java.util.Collections.enumeration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.mule.runtime.core.api.config.ConfigurationBuilder;
import org.mule.runtime.module.artifact.api.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.api.classloader.ClassLoaderFilter;
import org.mule.runtime.module.artifact.api.classloader.FilteringArtifactClassLoader;
import org.mule.tck.junit4.AbstractMuleContextTestCase;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.Test;

public class TestBootstrapRegistryConfigurationBuilderTestCase extends AbstractMuleContextTestCase {

  @Inject
  @Named("testObject1")
  private Object testObject1;

  @Inject
  @Named("testObject2")
  private Object testObject2;

  public TestBootstrapRegistryConfigurationBuilderTestCase() {
    setStartContext(false);
  }

  @Test
  public void usesAllPluginBootstrapFiles() throws Exception {
    muleContext.getInjector().inject(this);
    assertThat(testObject1, is(not(nullValue())));
    assertThat(testObject2, is(not(nullValue())));
  }

  @Override
  protected void addBuilders(List<ConfigurationBuilder> builders) {
    builders.add(0, createBootstrapServiceDiscovererContextBuilder());

    super.addBuilders(builders);
  }

  private TestBootstrapServiceDiscovererConfigurationBuilder createBootstrapServiceDiscovererContextBuilder() {
    try {
      final ClassLoaderFilter filter = mock(ClassLoaderFilter.class);
      when(filter.exportsClass(anyString())).thenReturn(true);

      final ArtifactClassLoader pluginClassLoader1 = mock(ArtifactClassLoader.class);
      when(pluginClassLoader1.getClassLoader()).thenReturn(this.getClass().getClassLoader());
      final List<ArtifactClassLoader> artifactClassLoaders = new ArrayList<>();
      artifactClassLoaders.add(new FilteringArtifactClassLoader(pluginClassLoader1, filter, emptyList()));

      final List<URL> urls = new ArrayList<>();
      urls.add(this.getClass().getResource("/plugin1-bootstrap.properties"));
      urls.add(this.getClass().getResource("/plugin2-bootstrap.properties"));
      when(pluginClassLoader1.findResources(BOOTSTRAP_PROPERTIES)).thenReturn(enumeration(urls));

      final ArtifactClassLoader appClassLoader = mock(ArtifactClassLoader.class);
      when(appClassLoader.getClassLoader()).thenReturn(this.getClass().getClassLoader());
      when(appClassLoader.findResources(BOOTSTRAP_PROPERTIES)).thenReturn(enumeration(emptyList()));
      final ClassLoader executionClassLoader = new FilteringArtifactClassLoader(appClassLoader, filter, emptyList());
      return new TestBootstrapServiceDiscovererConfigurationBuilder(getClass().getClassLoader(), executionClassLoader,
                                                                    artifactClassLoaders);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
