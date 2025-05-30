/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.policy;

import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_POLICY_PROVIDER;
import static org.mule.runtime.http.policy.api.SourcePolicyAwareAttributes.noAttributes;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.runtime.api.config.custom.ServiceConfigurator;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Disposable;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.ConfigurationBuilder;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.policy.Policy;
import org.mule.runtime.core.api.policy.PolicyInstance;
import org.mule.runtime.core.api.policy.PolicyProvider;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.policy.api.PolicyAwareAttributes;
import org.mule.runtime.policy.api.PolicyPointcutParameters;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.Before;
import org.junit.Test;

public class PolicyTestCase extends MuleArtifactFunctionalTestCase {

  private static final String POLICY_ID = "policyId";
  private static final AtomicBoolean processorWasDisposed = new AtomicBoolean(false);

  @Inject
  private PolicyProvider policyProvider;

  @Inject
  private SchedulerService schedulerService;

  @Override
  protected String getConfigFile() {
    return "test-policy-config.xml";
  }

  @Override
  public int getTestTimeoutSecs() {
    return super.getTestTimeoutSecs() * 5;
  }

  @Override
  protected void addBuilders(List<ConfigurationBuilder> builders) {
    super.addBuilders(builders);
    builders.add(customPolicyProviderConfigurationBuilder());
  }

  private ConfigurationBuilder customPolicyProviderConfigurationBuilder() {
    return new ConfigurationBuilder() {

      @Override
      public void configure(MuleContext muleContext) {
        final AtomicReference<Optional<PolicyInstance>> policyReference = new AtomicReference<>();
        muleContext.getCustomizationService().registerCustomServiceImpl(OBJECT_POLICY_PROVIDER,
                                                                        new TestPolicyProvider(policyReference));
      }

      @Override
      public void addServiceConfigurator(ServiceConfigurator serviceConfigurator) {
        // Nothing to do
      }
    };
  }

  @Before
  public void setUp() {
    processorWasDisposed.set(false);
  }

  @Test
  public void parsesPolicy() throws Exception {
    assertThat(policyProvider, instanceOf(TestPolicyProvider.class));
    TestPolicyProvider testPolicyProvider = (TestPolicyProvider) policyProvider;
    assertThat(testPolicyProvider.policyReference.get(), is(not(nullValue())));

    List<Policy> sourceParameterizedPolicies = testPolicyProvider.findSourceParameterizedPolicies(null);
    assertThat(sourceParameterizedPolicies.size(), equalTo(1));

    List<Policy> operationParameterizedPolicies = testPolicyProvider.findOperationParameterizedPolicies(null);
    assertThat(operationParameterizedPolicies.size(), equalTo(1));
  }

  @Test
  public void policyDisposalStopsSchedulers() {
    // Once the application is started with it's corresponding test policy. Test whether the policy-specific schedulers, generated
    // in the PolicyProcessingStrategy, are stopped once the whole application is
    // disposed.
    muleContext.dispose();
    assertThat(schedulerService.getSchedulers().size(), is(0));
  }

  private static class TestPolicyProvider implements PolicyProvider, Startable {

    private final AtomicReference<Optional<PolicyInstance>> policyReference;

    @Inject
    private PolicyInstance policyInstance;

    public TestPolicyProvider(AtomicReference<Optional<PolicyInstance>> policyReference) {
      this.policyReference = policyReference;
    }

    @Override
    public List<Policy> findSourceParameterizedPolicies(PolicyPointcutParameters policyPointcutParameters) {
      return policyReference.get().map(policy -> policy.getSourcePolicyChain()
          .map(sourceChain -> asList(new Policy(sourceChain, POLICY_ID)))
          .orElse(emptyList())).orElse(emptyList());
    }

    @Override
    public PolicyAwareAttributes sourcePolicyAwareAttributes() {
      return noAttributes();
    }

    @Override
    public boolean isSourcePoliciesAvailable() {
      return true;
    }

    @Override
    public boolean isOperationPoliciesAvailable() {
      return true;
    }

    @Override
    public List<Policy> findOperationParameterizedPolicies(PolicyPointcutParameters policyPointcutParameters) {
      return policyReference.get().map(policy -> policy.getOperationPolicyChain()
          .map(operationChain -> asList(new Policy(operationChain, "policyId")))
          .orElse(emptyList())).orElse(emptyList());
    }

    private PolicyInstance getPolicyFromRegistry() {
      return policyInstance;
    }

    @Override
    public void start() {
      if (policyReference.get() == null) {
        PolicyInstance policyInstance = getPolicyFromRegistry();
        policyReference.set(ofNullable(policyInstance));
      }
    }
  }

  public static class DisposeListenerMessageProcessor implements Processor, Disposable {

    public DisposeListenerMessageProcessor() {}

    @Override
    public CoreEvent process(CoreEvent event) throws MuleException {
      return event;
    }

    @Override
    public void dispose() {
      processorWasDisposed.set(true);
    }
  }
}
