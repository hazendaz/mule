/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.resources;

import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mule.runtime.extension.api.resources.ResourcesGenerator;
import org.mule.runtime.module.extension.internal.resources.test.ResourcesGeneratorContractTestCase;
import org.mule.tck.size.SmallTest;

import java.io.OutputStream;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;

import org.junit.Rule;
import org.junit.Test;

import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
public class AnnotationProcessorResourceGeneratorTestCase extends ResourcesGeneratorContractTestCase {

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ProcessingEnvironment processingEnvironment;

  @Override
  protected ResourcesGenerator buildGenerator() {
    return new AnnotationProcessorResourceGenerator(resourceFactories, processingEnvironment);
  }

  @Test
  public void write() throws Exception {
    FileObject file = mock(FileObject.class);
    when(processingEnvironment.getFiler().createResource(SOURCE_OUTPUT, EMPTY, RESOURCE_PATH)).thenReturn(file);

    OutputStream out = mock(OutputStream.class, RETURNS_DEEP_STUBS);
    when(file.openOutputStream()).thenReturn(out);

    generator.generateFor(extensionModel);

    verify(out).write(RESOURCE_CONTENT);
    verify(out).flush();
  }
}
