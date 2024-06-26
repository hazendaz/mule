/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.exception;

import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.core.api.event.CoreEvent;
import org.reactivestreams.Publisher;

public class GlobalErrorHandler extends ErrorHandler {

  @Override
  public Publisher<CoreEvent> apply(Exception exception) {
    throw new IllegalStateException("GlobalErrorHandlers should be used only as template for local ErrorHandlers");
  }

  public ErrorHandler createLocalErrorHandler(ComponentLocation flowLocation) {
    ErrorHandler local = new ErrorHandler();
    local.setName(this.name);
    local.setExceptionListeners(this.getExceptionListeners());
    local.setExceptionListenersLocation(flowLocation);
    return local;
  }
}
