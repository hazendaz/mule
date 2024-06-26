/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.extension.internal.loader.validator;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.mule.runtime.api.meta.model.parameter.ParameterGroupModel.DEFAULT_GROUP_NAME;
import static org.mule.runtime.config.api.dsl.CoreDslConstants.RAISE_ERROR_IDENTIFIER;
import static org.mule.runtime.config.internal.dsl.spring.BeanDefinitionFactory.CORE_ERROR_NS;
import static org.mule.runtime.config.internal.dsl.spring.BeanDefinitionFactory.TARGET_TYPE;
import static org.mule.runtime.config.internal.model.ApplicationModel.ERROR_MAPPING_IDENTIFIER;
import static org.mule.runtime.module.extension.internal.runtime.exception.ErrorMappingUtils.forEachErrorMappingDo;

import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.XmlDslModel;
import org.mule.runtime.api.meta.model.operation.HasOperationModels;
import org.mule.runtime.api.meta.model.operation.OperationModel;
import org.mule.runtime.api.meta.model.util.ExtensionWalker;
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.ast.api.ComponentParameterAst;
import org.mule.runtime.config.api.dsl.CoreDslConstants;
import org.mule.runtime.config.internal.dsl.model.extension.xml.property.OperationComponentModelModelProperty;
import org.mule.runtime.config.internal.dsl.spring.BeanDefinitionFactory;
import org.mule.runtime.config.internal.model.ApplicationModel;
import org.mule.runtime.extension.api.error.ErrorMapping;
import org.mule.runtime.extension.api.loader.ExtensionModelValidator;
import org.mule.runtime.extension.api.loader.Problem;
import org.mule.runtime.extension.api.loader.ProblemsReporter;

import java.util.Optional;

/**
 * {@link ExtensionModelValidator} which applies to {@link ExtensionModel}s which are XML based, as those that contain usages of
 * {@link CoreDslConstants#RAISE_ERROR_IDENTIFIER} or {@link ApplicationModel#ERROR_MAPPING_IDENTIFIER} within an
 * {@link OperationModel}, where each prefix must either match {@link BeanDefinitionFactory#CORE_ERROR_NS} or the current
 * namespace of the <module/> (which maps to the {@link XmlDslModel#getPrefix()}).
 *
 * @since 4.0
 */
public class CorrectPrefixesValidator implements ExtensionModelValidator {

  private static final String SEPARATOR = ":";
  public static final String TYPE_RAISE_ERROR_ATTRIBUTE = "type";
  public static final String EMPTY_TYPE_FORMAT_MESSAGE =
      "When using a %s the '%s' must not be null nor empty, offending operation '%s'";
  public static final String WRONG_VALUE_FORMAT_MESSAGE =
      "When using a %s the '%s' must either use the runtime or the custom namespace of the current module ('%s' or '%s') but found '%s', offending operation '%s'";

  @Override
  public void validate(ExtensionModel extensionModel, ProblemsReporter problemsReporter) {
    new ExtensionWalker() {

      @Override
      protected void onOperation(HasOperationModels owner, OperationModel operationModel) {
        operationModel.getModelProperty(OperationComponentModelModelProperty.class)
            .ifPresent(operationComponentModelModelProperty -> {
              searchAndValidate(extensionModel.getXmlDslModel().getPrefix(), operationModel,
                                operationComponentModelModelProperty.getBodyComponentModel(), problemsReporter);
            });
      }
    }.walk(extensionModel);
  }

  /**
   * Goes over the complete set of message processors inside the <body/> declaration, checking if any of those is a
   * {@link CoreDslConstants#RAISE_ERROR_IDENTIFIER} or {@link ApplicationModel#ERROR_MAPPING_IDENTIFIER} If it is, then asserts
   * the correct namespace of it (as XML <module/>s can throw exceptions of the the same namespace).
   *
   * @param namespace        namespace of the <module/>
   * @param operationModel   current operation of the <module/>
   * @param componentModel   XML element to validate, or its child elements.
   * @param problemsReporter gatherer of errors
   */
  private void searchAndValidate(String namespace, OperationModel operationModel, ComponentAst componentModel,
                                 ProblemsReporter problemsReporter) {
    if (componentModel.getIdentifier().equals(RAISE_ERROR_IDENTIFIER)) {
      validateRaiseError(namespace, operationModel, componentModel, problemsReporter);
    }

    forEachErrorMappingDo(componentModel, mappings -> mappings
        .forEach(mapping -> validateErrorMapping(namespace, operationModel, mapping, problemsReporter)));

    componentModel.directChildrenStream()
        .forEach(childComponentModel -> searchAndValidate(namespace, operationModel, childComponentModel, problemsReporter));
  }

  private void validateRaiseError(String moduleNamespace, OperationModel operationModel, ComponentAst raiseErrorComponentModel,
                                  ProblemsReporter problemsReporter) {
    genericValidation(moduleNamespace, operationModel, raiseErrorComponentModel, problemsReporter, TYPE_RAISE_ERROR_ATTRIBUTE,
                      RAISE_ERROR_IDENTIFIER);
  }

  private void validateErrorMapping(String moduleNamespace, OperationModel operationModel,
                                    ErrorMapping errorMappingComponentModel,
                                    ProblemsReporter problemsReporter) {
    genericValidation(moduleNamespace, operationModel, problemsReporter, TARGET_TYPE, ERROR_MAPPING_IDENTIFIER,
                      ofNullable(errorMappingComponentModel.getTarget()));
  }

  private static Optional<String> getRawParameterValue(ComponentAst componentAst, String parameterName) {
    return ofNullable(componentAst.getParameter(DEFAULT_GROUP_NAME, parameterName))
        .map(ComponentParameterAst::getResolvedRawValue);
  }

  private void genericValidation(String moduleNamespace, OperationModel operationModel, ComponentAst elementComponentModel,
                                 ProblemsReporter problemsReporter, String attributeToValidate,
                                 ComponentIdentifier workingIdentifier) {
    genericValidation(moduleNamespace, operationModel, problemsReporter, attributeToValidate, workingIdentifier,
                      getRawParameterValue(elementComponentModel, attributeToValidate));
  }

  private void genericValidation(String moduleNamespace, OperationModel operationModel, ProblemsReporter problemsReporter,
                                 String attributeToValidate, ComponentIdentifier workingIdentifier,
                                 final Optional<String> stringRepresentation) {
    if (!stringRepresentation.isPresent()) {
      problemsReporter.addError(new Problem(operationModel, format(
                                                                   EMPTY_TYPE_FORMAT_MESSAGE,
                                                                   workingIdentifier.toString(),
                                                                   attributeToValidate,
                                                                   operationModel.getName())));
    } else {
      int separator = stringRepresentation.get().indexOf(SEPARATOR);
      if (separator > 0) {
        String namespace = stringRepresentation.get().substring(0, separator).toUpperCase();
        if (!moduleNamespace.toUpperCase().equals(namespace)) {
          problemsReporter.addError(new Problem(operationModel, format(
                                                                       WRONG_VALUE_FORMAT_MESSAGE,
                                                                       workingIdentifier.toString(),
                                                                       attributeToValidate,
                                                                       CORE_ERROR_NS,
                                                                       moduleNamespace.toUpperCase(),
                                                                       namespace,
                                                                       operationModel.getName())));
        }
      }
    }
  }
}
