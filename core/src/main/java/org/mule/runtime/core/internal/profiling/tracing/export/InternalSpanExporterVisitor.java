/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.internal.profiling.tracing.export;

import org.mule.runtime.core.internal.profiling.tracing.event.span.export.optel.NoOpInternalSpanExporter;

/**
 * A visitor for {@link InternalSpanExporter}.
 *
 * @param <T> the type of the result of visiting the instance.
 */
public interface InternalSpanExporterVisitor<T> {

  /**
   * @param opentelemetrySpanExporter the {@link OpenTelemetrySpanExporter} to accept
   * @return the result of visiting the instance.
   */
  T accept(OpenTelemetrySpanExporter opentelemetrySpanExporter);

  /**
   * @param openTelemetrySpanExporter the {@link NoOpInternalSpanExporter} to accept
   * @return the result of visiting the instance.
   */
  T accept(NoOpInternalSpanExporter openTelemetrySpanExporter);
}