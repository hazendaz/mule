/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.artifact.api.classloader;

import static org.mule.runtime.module.artifact.api.classloader.ChildFirstLookupStrategy.CHILD_FIRST;
import static org.mule.runtime.module.artifact.api.classloader.DefaultArtifactClassLoaderFilter.NULL_CLASSLOADER_FILTER;
import static org.mule.runtime.module.artifact.api.classloader.ParentFirstLookupStrategy.PARENT_FIRST;
import static org.mule.runtime.module.artifact.api.classloader.RegionClassLoader.REGION_OWNER_CANNOT_BE_REMOVED_ERROR;
import static org.mule.runtime.module.artifact.api.classloader.RegionClassLoader.createCannotRemoveClassLoaderError;
import static org.mule.runtime.module.artifact.api.classloader.RegionClassLoader.createClassLoaderAlreadyInRegionError;
import static org.mule.runtime.module.artifact.api.classloader.RegionClassLoader.duplicatePackageMappingError;
import static org.mule.runtime.module.artifact.api.classloader.RegionClassLoader.illegalPackageMappingError;
import static org.mule.test.allure.AllureConstants.ClassloadingIsolationFeature.CLASSLOADING_ISOLATION;
import static org.mule.test.allure.AllureConstants.ClassloadingIsolationFeature.ClassloadingIsolationStory.ARTIFACT_CLASSLOADERS;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.enumeration;
import static java.util.Collections.list;
import static java.util.Collections.singleton;

import static org.hamcrest.MatcherAssert.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;

import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.module.artifact.api.classloader.test.TestArtifactClassLoader;
import org.mule.runtime.module.artifact.api.descriptor.ArtifactDescriptor;
import org.mule.runtime.module.artifact.api.descriptor.BundleDependency;
import org.mule.runtime.module.artifact.api.descriptor.BundleDescriptor;
import org.mule.runtime.module.artifact.api.descriptor.ClassLoaderConfiguration;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.util.EnumerationMatcher;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import org.hamcrest.CoreMatchers;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature(CLASSLOADING_ISOLATION)
@Story(ARTIFACT_CLASSLOADERS)
public class RegionClassLoaderTestCase extends AbstractMuleTestCase {

  protected static final String PACKAGE_NAME = "java.lang";
  protected static final String CLASS_NAME = PACKAGE_NAME + ".Object";
  private static final Class PARENT_LOADED_CLASS = Object.class;
  protected static final Class PLUGIN_LOADED_CLASS = String.class;
  private static final String RESOURCE_NAME = "dummy.txt";
  private static final String NON_EXPORTED_RESOURCE_NAME = "hidden.txt";
  public static final String APP_NAME = "testApp";
  protected static final String ARTIFACT_ID = "testAppId";
  private static final String GROUP_ID = "com.organization";
  private static final String SPECIFIC_ARTIFACT_ID = "test-artifact";
  private static final String SPECIFIC_ARTIFACT_ID_WITH_SPACES = "test-artifact-with-spaces";
  private static final String ARTIFACT_VERSION = "1.0.0";
  private static final String ARTIFACT_SNAPSHOT_TIMESTAMPED_VERSION = "1.0.0-20190514.200154-1";
  private static final String ARTIFACT_SNAPSHOT_VERSION = "1.0.0-SNAPSHOT";
  private static final String SPECIFIC_RESOURCE_FORMAT = "resource::" + GROUP_ID + ":" + SPECIFIC_ARTIFACT_ID + ":%s:%s:%s:%s";
  private static final String SPECIFIC_RESOURCE_FORMAT_WITH_SPACES =
      "resource::" + GROUP_ID + ":" + SPECIFIC_ARTIFACT_ID_WITH_SPACES + ":%s:%s:%s:%s";
  private static final String API_RESOURCE_NAME = "test-api.raml";
  private static final String API_RESOURE_NAME_WITH_SPACES = "raml with spaces.raml";
  private static final String API_FOLDER_NAME_WITH_SPACES = "folder with spaces";
  private static final String API_RESOURCE_NAME_WITH_SPACES_ENCODED = API_RESOURE_NAME_WITH_SPACES.replace(" ", "%20");
  private static final String API_FOLDER_NAME_WITH_SPACES_ENCODED = API_FOLDER_NAME_WITH_SPACES.replace(" ", "%20");


  private final URL APP_LOADED_RESOURCE;
  private final URL PLUGIN_LOADED_RESOURCE;
  private final URL PARENT_LOADED_RESOURCE;
  private final URL API_LOCATION;
  private final URL API_LOADED_RESOURCE;

  protected final ClassLoaderLookupPolicy lookupPolicy = mock(ClassLoaderLookupPolicy.class);
  protected final ArtifactDescriptor artifactDescriptor;
  private final URL API_WITH_SPACES_LOCATION;
  private final URL API_WITH_SPACES_LOADED_RESOURCE;
  private final URL API_WITH_SPACES_LOADED_RESOURCE_WITH_FOLDER_WITH_SPACES;

  protected TestApplicationClassLoader appClassLoader;
  protected TestArtifactClassLoader pluginClassLoader;

  public RegionClassLoaderTestCase() throws MalformedURLException {
    PARENT_LOADED_RESOURCE = new URL("file:///parent.txt");
    APP_LOADED_RESOURCE = new URL("file:///app.txt");
    PLUGIN_LOADED_RESOURCE = new URL("file:///plugin.txt");
    API_LOCATION = ClassUtils.getResource("com/organization/test-artifact/1.0.0/test-artifact-1.0.0-raml.zip", this.getClass());
    API_WITH_SPACES_LOCATION =
        ClassUtils.getResource("com/organization/test-artifact/1.0.0/test-artifact-with-spaces-1.0.0-raml.zip", this.getClass());
    API_LOADED_RESOURCE = new URL("jar:" + API_LOCATION.toString() + "!/" + API_RESOURCE_NAME);
    API_WITH_SPACES_LOADED_RESOURCE =
        new URL("jar:" + API_WITH_SPACES_LOCATION.toString() + "!/" + API_RESOURCE_NAME_WITH_SPACES_ENCODED);
    API_WITH_SPACES_LOADED_RESOURCE_WITH_FOLDER_WITH_SPACES = new URL("jar:" + API_WITH_SPACES_LOCATION.toString() + "!/"
        + API_FOLDER_NAME_WITH_SPACES_ENCODED + "/" + API_RESOURCE_NAME_WITH_SPACES_ENCODED);
    artifactDescriptor = new ArtifactDescriptor(APP_NAME);
  }

  @Test(expected = ClassNotFoundException.class)
  public void failsToLoadClassWhenIsNotDefinedInAnyClassLoader() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.loadClass(CLASS_NAME)).thenThrow(new ClassNotFoundException());

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    List<ArtifactClassLoader> classLoaders = createClassLoaders(regionClassLoader);

    classLoaders.forEach(classLoader -> regionClassLoader.addClassLoader(classLoader, NULL_CLASSLOADER_FILTER));

    when(lookupPolicy.getClassLookupStrategy(Object.class.getName())).thenReturn(CHILD_FIRST);
    regionClassLoader.loadClass(CLASS_NAME);
  }

  protected List<ArtifactClassLoader> createClassLoaders(RegionClassLoader parent) {
    appClassLoader = new TestApplicationClassLoader(parent);
    pluginClassLoader = new SubTestClassLoader(parent);

    List<ArtifactClassLoader> classLoaders = new LinkedList<>();
    Collections.addAll(classLoaders, appClassLoader, pluginClassLoader);
    return classLoaders;
  }


  @Test
  public void loadsParentClassWhenIsNotDefinedInAnyRegionClassLoader() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.loadClass(CLASS_NAME)).thenReturn(PARENT_LOADED_CLASS);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);

    List<ArtifactClassLoader> classLoaders = createClassLoaders(regionClassLoader);

    classLoaders.forEach(classLoader -> regionClassLoader.addClassLoader(classLoader, NULL_CLASSLOADER_FILTER));
    when(lookupPolicy.getClassLookupStrategy(Object.class.getName())).thenReturn(CHILD_FIRST);
    final Class loadedClass = regionClassLoader.loadClass(CLASS_NAME);
    assertThat(loadedClass, equalTo(PARENT_LOADED_CLASS));
  }

  @Test
  public void loadsClassFromRegionMemberWhenPackageMappingDefined() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.loadClass(CLASS_NAME)).thenReturn(PARENT_LOADED_CLASS);

    when(lookupPolicy.getClassLookupStrategy(Object.class.getName())).thenReturn(CHILD_FIRST);
    when(lookupPolicy.getPackageLookupStrategy(PACKAGE_NAME)).thenReturn(CHILD_FIRST);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);

    regionClassLoader.addClassLoader(appClassLoader, NULL_CLASSLOADER_FILTER);
    regionClassLoader.addClassLoader(pluginClassLoader,
                                     new DefaultArtifactClassLoaderFilter(singleton(PACKAGE_NAME), emptySet()));
    pluginClassLoader.addClass(CLASS_NAME, PLUGIN_LOADED_CLASS);
    final Class loadedClass = regionClassLoader.loadClass(CLASS_NAME);
    assertThat(loadedClass, equalTo(PLUGIN_LOADED_CLASS));
  }

  @Test
  public void returnsNullResourceWhenIsNotDefinedInAnyClassLoader() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.getResource(RESOURCE_NAME)).thenReturn(null);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    List<ArtifactClassLoader> classLoaders = createClassLoaders(regionClassLoader);

    classLoaders.forEach(classLoader -> regionClassLoader.addClassLoader(classLoader, NULL_CLASSLOADER_FILTER));

    URL resource = regionClassLoader.getResource(RESOURCE_NAME);
    assertThat(resource, CoreMatchers.equalTo(null));
  }

  @Test
  public void loadsResourceFromParentWhenIsNotDefinedInAnyRegionClassLoader() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.getResource(RESOURCE_NAME)).thenReturn(PARENT_LOADED_RESOURCE);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    regionClassLoader.addClassLoader(appClassLoader, NULL_CLASSLOADER_FILTER);
    regionClassLoader.addClassLoader(pluginClassLoader,
                                     new DefaultArtifactClassLoaderFilter(emptySet(), singleton(RESOURCE_NAME)));

    URL resource = regionClassLoader.getResource(RESOURCE_NAME);
    assertThat(resource, CoreMatchers.equalTo(PARENT_LOADED_RESOURCE));
  }

  @Test
  public void loadsResourceFromRegionMemberWhenIsDefinedInRegionAndParentClassLoader() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.getResource(RESOURCE_NAME)).thenReturn(PARENT_LOADED_RESOURCE);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);

    appClassLoader.addResource(RESOURCE_NAME, APP_LOADED_RESOURCE);
    regionClassLoader.addClassLoader(appClassLoader, new DefaultArtifactClassLoaderFilter(emptySet(), emptySet()));

    pluginClassLoader.addResource(RESOURCE_NAME, PLUGIN_LOADED_RESOURCE);
    regionClassLoader.addClassLoader(pluginClassLoader,
                                     new DefaultArtifactClassLoaderFilter(emptySet(), singleton(RESOURCE_NAME)));

    URL resource = regionClassLoader.getResource(RESOURCE_NAME);
    assertThat(resource, CoreMatchers.equalTo(PLUGIN_LOADED_RESOURCE));
  }

  @Test
  public void getsAllResources() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.getResources(RESOURCE_NAME)).thenReturn(enumeration(singleton(PARENT_LOADED_RESOURCE)));

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);

    regionClassLoader.addClassLoader(appClassLoader, new DefaultArtifactClassLoaderFilter(emptySet(), singleton(RESOURCE_NAME)));
    appClassLoader.addResource(RESOURCE_NAME, APP_LOADED_RESOURCE);
    pluginClassLoader.addResource(RESOURCE_NAME, APP_LOADED_RESOURCE);
    regionClassLoader.addClassLoader(pluginClassLoader,
                                     new DefaultArtifactClassLoaderFilter(emptySet(), singleton(RESOURCE_NAME)));

    final Enumeration<URL> resources = regionClassLoader.getResources(RESOURCE_NAME);

    List<URL> expectedResources = new LinkedList<>();
    expectedResources.add(APP_LOADED_RESOURCE);
    expectedResources.add(PLUGIN_LOADED_RESOURCE);
    expectedResources.add(PARENT_LOADED_RESOURCE);

    assertThat(resources, EnumerationMatcher.equalTo(expectedResources));
  }

  @Test
  public void verifiesThatClassLoaderIsNotAddedTwice() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.getResource(RESOURCE_NAME)).thenReturn(PARENT_LOADED_RESOURCE);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);

    regionClassLoader.addClassLoader(appClassLoader, NULL_CLASSLOADER_FILTER);

    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class,
                     () -> regionClassLoader.addClassLoader(appClassLoader, NULL_CLASSLOADER_FILTER));
    assertThat(thrown.getMessage(), containsString(createClassLoaderAlreadyInRegionError(appClassLoader.getArtifactId())));
  }

  @Test
  public void failsToAddClassLoaderThatOverridesPackageMapping() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(lookupPolicy.getPackageLookupStrategy(anyString())).thenReturn(CHILD_FIRST);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);

    regionClassLoader.addClassLoader(appClassLoader, new DefaultArtifactClassLoaderFilter(singleton(PACKAGE_NAME), emptySet()));
    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> regionClassLoader
        .addClassLoader(pluginClassLoader,
                        new DefaultArtifactClassLoaderFilter(singleton(PACKAGE_NAME), emptySet())));
    assertThat(thrown.getMessage(),
               containsString(duplicatePackageMappingError(PACKAGE_NAME, appClassLoader, pluginClassLoader)));
  }

  @Test
  public void failsToAddClassLoaderThatOverridesLookupPolicy() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(lookupPolicy.getPackageLookupStrategy(anyString())).thenReturn(PARENT_FIRST);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> regionClassLoader
        .addClassLoader(appClassLoader, new DefaultArtifactClassLoaderFilter(singleton(PACKAGE_NAME), emptySet())));
    assertThat(thrown.getMessage(), containsString(illegalPackageMappingError(PACKAGE_NAME, PARENT_FIRST)));
  }

  @Test
  public void doesNotRemoveClassLoaderIfNotARegionMember() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.getResource(RESOURCE_NAME)).thenReturn(PARENT_LOADED_RESOURCE);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);

    assertThat(regionClassLoader.removeClassLoader(appClassLoader), is(false));
  }

  @Test
  public void removesClassLoaderMember() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.getResource(RESOURCE_NAME)).thenReturn(PARENT_LOADED_RESOURCE);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    regionClassLoader.addClassLoader(appClassLoader, NULL_CLASSLOADER_FILTER);
    regionClassLoader.addClassLoader(pluginClassLoader, NULL_CLASSLOADER_FILTER);

    assertThat(regionClassLoader.removeClassLoader(pluginClassLoader), is(true));
    assertThat(regionClassLoader.getArtifactPluginClassLoaders(), is(empty()));
  }

  @Test
  public void failsToRemoveRegionOwner() throws Exception {

    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.getResource(RESOURCE_NAME)).thenReturn(PARENT_LOADED_RESOURCE);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    regionClassLoader.addClassLoader(appClassLoader, NULL_CLASSLOADER_FILTER);

    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> regionClassLoader.removeClassLoader(appClassLoader));
    assertThat(thrown.getMessage(), containsString(REGION_OWNER_CANNOT_BE_REMOVED_ERROR));
  }

  @Test
  public void failsToRemoveRegionMemberIfExportsPackages() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.getResource(RESOURCE_NAME)).thenReturn(PARENT_LOADED_RESOURCE);
    when(lookupPolicy.getPackageLookupStrategy(anyString())).thenReturn(CHILD_FIRST);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    regionClassLoader.addClassLoader(appClassLoader, NULL_CLASSLOADER_FILTER);
    regionClassLoader.addClassLoader(pluginClassLoader, new DefaultArtifactClassLoaderFilter(singleton("org.foo"), emptySet()));

    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> regionClassLoader.removeClassLoader(pluginClassLoader));
    assertThat(thrown.getMessage(), containsString(createCannotRemoveClassLoaderError(appClassLoader.getArtifactId())));
  }

  @Test
  public void failsToRemoveRegionMemberIfExportsResources() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    when(parentClassLoader.getResource(RESOURCE_NAME)).thenReturn(PARENT_LOADED_RESOURCE);

    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    regionClassLoader.addClassLoader(appClassLoader, NULL_CLASSLOADER_FILTER);
    regionClassLoader.addClassLoader(pluginClassLoader,
                                     new DefaultArtifactClassLoaderFilter(emptySet(), singleton("META-INF/pom.xml")));

    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> regionClassLoader.removeClassLoader(pluginClassLoader));
    assertThat(thrown.getMessage(), containsString(createCannotRemoveClassLoaderError(appClassLoader.getArtifactId())));
  }

  @Test
  public void getsPluginsClassLoaders() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    regionClassLoader.addClassLoader(appClassLoader, NULL_CLASSLOADER_FILTER);
    regionClassLoader.addClassLoader(pluginClassLoader, NULL_CLASSLOADER_FILTER);

    assertThat(regionClassLoader.getArtifactPluginClassLoaders(), contains(pluginClassLoader));
  }

  @Test
  public void getResourceUsingNotNormalizedPath() {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    pluginClassLoader.addResource(RESOURCE_NAME, PLUGIN_LOADED_RESOURCE);

    when(lookupPolicy.getPackageLookupStrategy(PACKAGE_NAME)).thenReturn(CHILD_FIRST);

    regionClassLoader.addClassLoader(pluginClassLoader,
                                     new DefaultArtifactClassLoaderFilter(singleton(PACKAGE_NAME), singleton(RESOURCE_NAME)));

    URL resource = regionClassLoader.findResource("root/../dummy.txt");

    assertThat(resource, is(PLUGIN_LOADED_RESOURCE));
  }

  @Test
  public void getResourcesUsingNotNormalizedPath() throws IOException {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    pluginClassLoader.addResource(RESOURCE_NAME, PLUGIN_LOADED_RESOURCE);

    when(lookupPolicy.getPackageLookupStrategy(PACKAGE_NAME)).thenReturn(CHILD_FIRST);

    regionClassLoader.addClassLoader(pluginClassLoader,
                                     new DefaultArtifactClassLoaderFilter(singleton(PACKAGE_NAME), singleton(RESOURCE_NAME)));

    Enumeration<URL> resources = regionClassLoader.findResources("root/../dummy.txt");

    List<URL> expectedResources = new LinkedList<>();
    expectedResources.add(PLUGIN_LOADED_RESOURCE);

    assertThat(resources, EnumerationMatcher.equalTo(expectedResources));
  }

  @Test
  public void addClassloaderWithResourcesUsingNotNormalizedPath() throws IOException {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    pluginClassLoader.addResource(RESOURCE_NAME, PLUGIN_LOADED_RESOURCE);

    when(lookupPolicy.getPackageLookupStrategy(PACKAGE_NAME)).thenReturn(CHILD_FIRST);

    regionClassLoader.addClassLoader(pluginClassLoader,
                                     new DefaultArtifactClassLoaderFilter(singleton(PACKAGE_NAME),
                                                                          singleton("root/../dummy.txt")));

    URL resource = regionClassLoader.findResource(RESOURCE_NAME);

    assertThat(resource, is(PLUGIN_LOADED_RESOURCE));
  }

  @Test
  public void findsExportedResourceFromSpecificArtifact() {
    getResourceFromExportingArtifact(format(SPECIFIC_RESOURCE_FORMAT, ARTIFACT_VERSION, "mule-plugin", "jar", RESOURCE_NAME),
                                     PLUGIN_LOADED_RESOURCE);
  }

  @Test
  public void findsExportedResourceFromSpecificArtifactWithNoVersion() {
    getResourceFromExportingArtifact(format(SPECIFIC_RESOURCE_FORMAT, "*", "mule-plugin", "jar", RESOURCE_NAME),
                                     PLUGIN_LOADED_RESOURCE);
  }

  @Test
  public void findsExportedResourceFromSpecificArtifactWithWrongVersion() {
    getResourceFromExportingArtifact(format(SPECIFIC_RESOURCE_FORMAT, "1.2.0", "mule-plugin", "jar", RESOURCE_NAME),
                                     PLUGIN_LOADED_RESOURCE);
  }

  @Test
  public void doesNotFindNonExportedResourceFromSpecificArtifact() {
    getResourceFromExportingArtifact(format(SPECIFIC_RESOURCE_FORMAT, ARTIFACT_VERSION, "mule-plugin", "jar",
                                            NON_EXPORTED_RESOURCE_NAME),
                                     null);
  }

  @Test
  public void findsResourceFromRamlApi() throws Exception {
    getResourceFromApiArtifact("raml", format(SPECIFIC_RESOURCE_FORMAT, ARTIFACT_VERSION, "raml", "zip", API_RESOURCE_NAME),
                               API_LOADED_RESOURCE);
  }

  @Test
  public void findsResourceWithSpacesFromRamlApi() throws Exception {
    getResourceFromApiArtifact("raml", format(SPECIFIC_RESOURCE_FORMAT_WITH_SPACES, ARTIFACT_VERSION, "raml", "zip",
                                              API_RESOURE_NAME_WITH_SPACES),
                               SPECIFIC_ARTIFACT_ID_WITH_SPACES, API_WITH_SPACES_LOCATION, API_WITH_SPACES_LOADED_RESOURCE);
  }

  @Test
  public void findsResourceFromRamlApiInsideFolderWithSpaces() throws Exception {
    getResourceFromApiArtifact("raml", format(SPECIFIC_RESOURCE_FORMAT_WITH_SPACES, ARTIFACT_VERSION, "raml", "zip",
                                              API_FOLDER_NAME_WITH_SPACES + "/" + API_RESOURE_NAME_WITH_SPACES),
                               SPECIFIC_ARTIFACT_ID_WITH_SPACES, API_WITH_SPACES_LOCATION,
                               API_WITH_SPACES_LOADED_RESOURCE_WITH_FOLDER_WITH_SPACES);
  }

  @Test
  public void findsResourceFromRamlFragment() throws Exception {
    getResourceFromApiArtifact("raml-fragment",
                               format(SPECIFIC_RESOURCE_FORMAT, ARTIFACT_VERSION, "raml-fragment", "zip", API_RESOURCE_NAME),
                               API_LOADED_RESOURCE);
  }

  @Test
  public void findsResourceFromOasApi() throws Exception {
    getResourceFromApiArtifact("oas", format(SPECIFIC_RESOURCE_FORMAT, ARTIFACT_VERSION, "oas", "zip", API_RESOURCE_NAME),
                               API_LOADED_RESOURCE);
  }

  @Test
  public void findsResourceFromWsdlApi() throws Exception {
    getResourceFromApiArtifact("wsdl", format(SPECIFIC_RESOURCE_FORMAT, ARTIFACT_VERSION, "wsdl", "zip", API_RESOURCE_NAME),
                               API_LOADED_RESOURCE);
  }

  @Test
  public void doesNotFindNonExistentResourceFromApi() throws Exception {
    getResourceFromApiArtifact("raml", format(SPECIFIC_RESOURCE_FORMAT, ARTIFACT_VERSION, "raml", "zip", RESOURCE_NAME), null);
  }

  @Test
  public void doesNotFindResourceFromApiWithNoVersion() throws Exception {
    getResourceFromApiArtifact("raml", format(SPECIFIC_RESOURCE_FORMAT, "*", "raml", "zip", RESOURCE_NAME), null);
  }

  @Test
  public void doesNotFindResourceFromApiWithWrongVersion() throws Exception {
    getResourceFromApiArtifact("raml", format(SPECIFIC_RESOURCE_FORMAT, "1.5.6-SNAPSHOT", "raml", "zip", RESOURCE_NAME), null);
  }

  @Test
  public void remembersRamlApiClassLoader() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    ArtifactDescriptor appDescriptor = mock(ArtifactDescriptor.class);
    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, appDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    ClassLoaderConfiguration classLoaderConfiguration = new ClassLoaderConfiguration.ClassLoaderConfigurationBuilder()
        .dependingOn(singleton(new BundleDependency.Builder()
            .setBundleUri(API_LOCATION.toURI())
            .setDescriptor(new BundleDescriptor.Builder()
                .setGroupId(GROUP_ID)
                .setArtifactId(SPECIFIC_ARTIFACT_ID)
                .setVersion(ARTIFACT_VERSION)
                .setBaseVersion(ARTIFACT_VERSION)
                .setClassifier("raml")
                .setType("zip")
                .build())
            .build()))
        .build();

    when(appDescriptor.getClassLoaderConfiguration()).thenReturn(classLoaderConfiguration);

    String apiResource = format(SPECIFIC_RESOURCE_FORMAT, ARTIFACT_VERSION, "raml", "zip", API_RESOURCE_NAME);
    assertThat(regionClassLoader.findResource(apiResource), is(API_LOADED_RESOURCE));
    assertThat(regionClassLoader.findResource(apiResource), is(API_LOADED_RESOURCE));
    assertThat(regionClassLoader.findResource(apiResource), is(API_LOADED_RESOURCE));
    assertThat(regionClassLoader.findResource(apiResource), is(API_LOADED_RESOURCE));
    assertThat(regionClassLoader.findResource(apiResource), is(API_LOADED_RESOURCE));
    verify(appDescriptor, times(1)).getClassLoaderConfiguration();
  }

  @Test
  public void normalizedBaseVersionForSnapshots() throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    ArtifactDescriptor appDescriptor = mock(ArtifactDescriptor.class);
    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, appDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    ClassLoaderConfiguration classLoaderConfiguration = new ClassLoaderConfiguration.ClassLoaderConfigurationBuilder()
        .dependingOn(singleton(new BundleDependency.Builder()
            .setBundleUri(API_LOCATION.toURI())
            .setDescriptor(new BundleDescriptor.Builder()
                .setGroupId(GROUP_ID)
                .setArtifactId(SPECIFIC_ARTIFACT_ID)
                .setVersion(ARTIFACT_SNAPSHOT_TIMESTAMPED_VERSION)
                .setBaseVersion(ARTIFACT_SNAPSHOT_VERSION)
                .setClassifier("raml")
                .setType("zip")
                .build())
            .build()))
        .build();

    when(appDescriptor.getClassLoaderConfiguration()).thenReturn(classLoaderConfiguration);

    String apiResource = format(SPECIFIC_RESOURCE_FORMAT, ARTIFACT_SNAPSHOT_VERSION, "raml", "zip", API_RESOURCE_NAME);
    assertThat(regionClassLoader.findResource(apiResource), is(API_LOADED_RESOURCE));
    verify(appDescriptor).getClassLoaderConfiguration();
  }

  @Test
  public void findExportedPackageAsResource() throws Exception {
    findExportedPackageAsResource("com/mycompany/SomeClass.class", new URL("http://com.mycompany/SomeClass.class"),
                                  "com.mycompany");
  }

  @Test
  public void findExportedClassInRootPackage() throws Exception {
    findExportedPackageAsResource("Root.class", new URL("http://com.mycompany/Root.class"), "");
  }

  @Test
  public void findExportedPackageClasses() throws Exception {
    findExportedPackageClasses("com/mycompany", "com.mycompany",
                               asList("com/mycompany/SomeClass.class", "com/mycompany/SomeOtherClass.class"),
                               asList(new URL("http://com.mycompany/SomeClass.class"),
                                      new URL("http://com.mycompany/SomeOtherClass.class")));
  }

  @Test
  public void findExportedPackageClassesWithEndSlash() throws Exception {
    findExportedPackageClasses("com/mycompany/", "com.mycompany",
                               asList("com/mycompany/SomeClass.class", "com/mycompany/SomeOtherClass.class"),
                               asList(new URL("http://com.mycompany/SomeClass.class"),
                                      new URL("http://com.mycompany/SomeOtherClass.class")));
  }


  @Test
  public void findExportedPackageClassesInRootPackage() throws Exception {
    findExportedPackageClasses("", "",
                               asList("SomeClass.class", "SomeOtherClass.class"),
                               asList(new URL("http://com.mycompany/SomeClass.class"),
                                      new URL("http://com.mycompany/SomeOtherClass.class")));
  }

  private void findExportedPackageAsResource(String resource, URL resourceExpectedUrl, String resourcePackage) {
    ClassLoader parentClassLoader = mock(ClassLoader.class);
    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);

    appClassLoader.addResource(resource, resourceExpectedUrl);
    when(lookupPolicy.getPackageLookupStrategy(resourcePackage)).thenReturn(CHILD_FIRST);

    regionClassLoader.addClassLoader(appClassLoader,
                                     new DefaultArtifactClassLoaderFilter(singleton(resourcePackage), emptySet()));

    URL result = regionClassLoader.findResource(resource);
    assertThat(result, is(resourceExpectedUrl));
  }

  private void findExportedPackageClasses(String resource, String exportedPackage, List<String> classes, List<URL> urls)
      throws IOException {
    ClassLoader parentClassLoader = mock(ClassLoader.class);
    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);

    for (int i = 0; i < classes.size(); i++) {
      appClassLoader.addResource(classes.get(i), urls.get(i));
    }

    when(lookupPolicy.getPackageLookupStrategy(exportedPackage)).thenReturn(CHILD_FIRST);
    regionClassLoader.addClassLoader(appClassLoader,
                                     new DefaultArtifactClassLoaderFilter(singleton(exportedPackage), emptySet()));
    Enumeration<URL> resources = regionClassLoader.findResources(resource);
    List<URL> resourcesAsList = list(resources);
    assertThat(resourcesAsList, containsInAnyOrder(urls.toArray()));
  }

  private void getResourceFromExportingArtifact(String resource, URL expectedResult) {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, artifactDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    getResourceFromExportingArtifact(resource, expectedResult, regionClassLoader);
  }

  private void getResourceFromExportingArtifact(String resource, URL expectedResult, RegionClassLoader regionClassLoader) {
    ArtifactClassLoader pluginClassloader = mock(ArtifactClassLoader.class);
    ArtifactDescriptor pluginDescriptor = mock(ArtifactDescriptor.class);

    when(lookupPolicy.getPackageLookupStrategy(PACKAGE_NAME)).thenReturn(CHILD_FIRST);
    when(pluginClassloader.getArtifactDescriptor()).thenReturn(pluginDescriptor);
    when(pluginDescriptor.getBundleDescriptor()).thenReturn(new BundleDescriptor.Builder()
        .setGroupId(GROUP_ID)
        .setArtifactId(SPECIFIC_ARTIFACT_ID)
        .setVersion(ARTIFACT_VERSION)
        .setBaseVersion(ARTIFACT_VERSION)
        .setClassifier("mule-plugin")
        .setType("jar")
        .build());
    when(pluginClassloader.findResource(RESOURCE_NAME)).thenReturn(PLUGIN_LOADED_RESOURCE);
    when(pluginClassloader.findResource(NON_EXPORTED_RESOURCE_NAME)).thenReturn(PLUGIN_LOADED_RESOURCE);

    regionClassLoader.addClassLoader(pluginClassloader,
                                     new DefaultArtifactClassLoaderFilter(singleton(PACKAGE_NAME),
                                                                          singleton("dummy.txt")));

    URL result = regionClassLoader.findResource(resource);

    assertThat(result, is(expectedResult));
  }

  private void getResourceFromApiArtifact(String apiKind, String resource, URL expectedResult) throws Exception {
    getResourceFromApiArtifact(apiKind, resource, SPECIFIC_ARTIFACT_ID, API_LOCATION, expectedResult);
  }

  private void getResourceFromApiArtifact(String apiKind, String resource, String artifactId, URL apiLocation, URL expectedResult)
      throws Exception {
    final ClassLoader parentClassLoader = mock(ClassLoader.class);
    ArtifactDescriptor appDescriptor = mock(ArtifactDescriptor.class);
    RegionClassLoader regionClassLoader = new RegionClassLoader(ARTIFACT_ID, appDescriptor, parentClassLoader, lookupPolicy);
    createClassLoaders(regionClassLoader);
    ClassLoaderConfiguration classLoaderConfiguration = new ClassLoaderConfiguration.ClassLoaderConfigurationBuilder()
        .dependingOn(singleton(new BundleDependency.Builder()
            .setBundleUri(apiLocation.toURI())
            .setDescriptor(new BundleDescriptor.Builder()
                .setGroupId(GROUP_ID)
                .setArtifactId(artifactId)
                .setVersion(ARTIFACT_VERSION)
                .setBaseVersion(ARTIFACT_VERSION)
                .setClassifier(apiKind)
                .setType("zip")
                .build())
            .build()))
        .build();

    when(appDescriptor.getClassLoaderConfiguration()).thenReturn(classLoaderConfiguration);

    URL result = regionClassLoader.findResource(resource);

    assertThat(result, is(expectedResult));
  }

  public static class TestApplicationClassLoader extends TestArtifactClassLoader {

    public TestApplicationClassLoader(ClassLoader parent) {
      super(parent);
    }
  }

  // Used to ensure that the composite classloader is able to access
  // protected methods in subclasses by reflection
  public static class SubTestClassLoader extends TestArtifactClassLoader {

    SubTestClassLoader(ClassLoader parent) {
      super(parent);
    }
  }
}
