/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.config;

import static org.mule.runtime.api.connection.ConnectionValidationResult.failure;
import static org.mule.runtime.api.connection.ConnectionValidationResult.success;

import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;

import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Disposable;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.Lifecycle;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.meta.model.config.ConfigurationModel;
import org.mule.runtime.api.notification.NotificationDispatcher;
import org.mule.runtime.core.api.Injector;
import org.mule.runtime.core.api.retry.RetryNotifier;
import org.mule.runtime.core.api.retry.policy.RetryPolicyExhaustedException;
import org.mule.runtime.core.api.retry.policy.RetryPolicyTemplate;
import org.mule.runtime.core.api.retry.policy.SimpleRetryPolicyTemplate;
import org.mule.runtime.core.internal.connection.ConnectionManagerAdapter;
import org.mule.runtime.core.internal.connection.DefaultConnectivityTesterFactory;
import org.mule.runtime.extension.api.runtime.config.ConfigurationInstance;
import org.mule.runtime.extension.api.runtime.config.ConfigurationState;
import org.mule.tck.SimpleUnitTestSupportSchedulerService;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;
import org.mule.tck.util.TestTimeSupplier;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.verification.VerificationMode;

@SmallTest
@RunWith(Parameterized.class)
public class LifecycleAwareConfigurationInstanceTestCase extends AbstractMuleTestCase {

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  protected static final int RECONNECTION_MAX_ATTEMPTS = 5;
  private static final int RECONNECTION_FREQ = 100;
  private static final String NAME = "name";

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return asList(new Object[][] {
        {"With provider",
            mock(ConnectionProvider.class, withSettings().extraInterfaces(Lifecycle.class))},
        {"Without provider", null}
    });
  }

  @Mock
  private ConfigurationModel configurationModel;

  @Mock
  private ConfigurationState configurationState;

  @Mock
  protected ConnectionManagerAdapter connectionManager;

  protected Lifecycle value = mock(Lifecycle.class, withSettings().extraInterfaces(Component.class));
  protected RetryPolicyTemplate retryPolicyTemplate;
  protected Optional<ConnectionProvider> connectionProvider;
  protected LifecycleAwareConfigurationInstance configurationInstance;
  protected Injector injector;

  public LifecycleAwareConfigurationInstanceTestCase(String name, ConnectionProvider connectionProvider) {
    this.connectionProvider = ofNullable(connectionProvider);
  }

  private final TestTimeSupplier timeSupplier = new TestTimeSupplier(currentTimeMillis());

  @Before
  public void setUp() throws Exception {
    retryPolicyTemplate = createRetryTemplate();
    retryPolicyTemplate.setNotifier(mock(RetryNotifier.class));

    injector = mock(Injector.class);
    configurationInstance = createConfigurationInstance();
  }

  protected RetryPolicyTemplate createRetryTemplate() {
    return new TestSimpleRetryPolicyTemplate(RECONNECTION_FREQ, RECONNECTION_MAX_ATTEMPTS);
  }

  @After
  public void after() {
    configurationInstance.dispose();
  }

  protected LifecycleAwareConfigurationInstance createConfigurationInstance() throws MuleException {
    if (connectionProvider.isPresent()) {
      reset(connectionProvider.get());
    }
    setup(connectionManager);

    final var lifecycleAwareConfigurationInstance = new LifecycleAwareConfigurationInstance(NAME,
                                                                                            configurationModel,
                                                                                            value,
                                                                                            configurationState,
                                                                                            connectionProvider);
    lifecycleAwareConfigurationInstance.setTimeSupplier(timeSupplier);
    lifecycleAwareConfigurationInstance.setNotificationFirer(mock(NotificationDispatcher.class));
    lifecycleAwareConfigurationInstance.setConnectionManager(connectionManager);
    lifecycleAwareConfigurationInstance.setInjector(injector);

    final var defaultConnectivityTesterFactory = new DefaultConnectivityTesterFactory();
    defaultConnectivityTesterFactory.setLockFactory(lockId -> new ReentrantLock());
    defaultConnectivityTesterFactory.setSchedulerService(new SimpleUnitTestSupportSchedulerService());
    defaultConnectivityTesterFactory.setConnectionManager(connectionManager);
    lifecycleAwareConfigurationInstance.setConnectivityTesterFactory(defaultConnectivityTesterFactory);

    return lifecycleAwareConfigurationInstance;
  }

  private void setup(ConnectionManagerAdapter connectionManager) {
    if (connectionProvider.isPresent()) {
      when(connectionManager.getRetryTemplateFor(connectionProvider.get())).thenReturn(retryPolicyTemplate);
      when(connectionManager.testConnectivity(Mockito.any(ConfigurationInstance.class))).thenReturn(success());
    }
  }

  private void reset(Object object) {
    Mockito.reset(object);
    if (object instanceof ConnectionManagerAdapter) {
      setup((ConnectionManagerAdapter) object);
    }
  }

  @Test
  public void valueInjected() throws Exception {
    configurationInstance.initialise();
    verify(injector).inject(value);
    if (connectionProvider.isPresent()) {
      verify(injector).inject(connectionProvider.get());
    } else {
      verify(injector, never()).inject(any(ConnectionProvider.class));
    }
  }

  @Test
  public void connectionBound() throws Exception {
    configurationInstance.initialise();
    assertBound();
  }

  private void assertBound() throws Exception {
    if (connectionProvider.isPresent()) {
      verify(connectionManager, times(1)).bind(value, connectionProvider.get());
    } else {
      verify(connectionManager, never()).bind(same(value), ArgumentMatchers.any());
    }
  }

  private VerificationMode getBindingVerificationMode() {
    return connectionProvider.map(p -> times(1)).orElse(never());
  }

  @Test
  public void connectionReBoundfterStopStart() throws Exception {
    connectionBound();
    configurationInstance.start();
    configurationInstance.stop();
    verify(connectionManager, getBindingVerificationMode()).unbind(value);

    reset(connectionManager);
    configurationInstance.start();
    assertBound();
  }

  @Test
  public void valueInitialised() throws Exception {
    configurationInstance.initialise();
    verify((Initialisable) value).initialise();
    if (connectionProvider.isPresent()) {
      verify((Initialisable) connectionProvider.get()).initialise();
    }
  }

  @Test
  public void valueStarted() throws Exception {
    configurationInstance.start();
    verify((Startable) value).start();
    if (connectionProvider.isPresent()) {
      verify((Startable) connectionProvider.get()).start();
    }
  }

  @Test
  public void testConnectivityUponStart() throws Exception {
    configurationInstance.initialise();
    if (connectionProvider.isPresent()) {
      valueStarted();
      verify(connectionManager).testConnectivity(configurationInstance);
    }
  }

  @Test
  public void testConnectivityFailsUponStart() throws Exception {
    if (connectionProvider.isPresent()) {
      Exception connectionException = new ConnectionException("Oops!");
      when(connectionManager.testConnectivity(configurationInstance))
          .thenReturn(failure(connectionException.getMessage(), connectionException));

      configurationInstance.initialise();
      try {
        configurationInstance.start();
        fail("Was expecting connectivity testing to fail");
      } catch (Exception e) {
        verify(connectionManager, times(RECONNECTION_MAX_ATTEMPTS + 1)).testConnectivity(configurationInstance);
        assertThat(e.getCause(), is(instanceOf(RetryPolicyExhaustedException.class)));
      }
    }
  }

  @Test
  public void valueStopped() throws Exception {
    configurationInstance.initialise();
    configurationInstance.start();
    configurationInstance.stop();
    verify((Stoppable) value).stop();
    if (connectionProvider.isPresent()) {
      verify((Stoppable) connectionProvider.get()).stop();
    }
  }

  @Test
  public void connectionUnbound() throws Exception {
    configurationInstance.initialise();
    configurationInstance.start();
    configurationInstance.stop();
    if (connectionProvider.isPresent()) {
      verify(connectionManager).unbind(value);
    } else {
      verify(connectionManager, never()).unbind(ArgumentMatchers.any());
    }
  }

  @Test
  public void valueDisposed() throws Exception {
    configurationInstance.initialise();
    configurationInstance.dispose();
    verify((Disposable) value).dispose();
    if (connectionProvider.isPresent()) {
      verify((Disposable) connectionProvider.get()).dispose();
    }
  }

  @Test
  public void getName() {
    assertThat(configurationInstance.getName(), is(NAME));
  }

  @Test
  public void getModel() {
    assertThat(configurationInstance.getModel(), is(sameInstance(configurationModel)));
  }

  @Test
  public void getValue() {
    assertThat(configurationInstance.getValue(), is(sameInstance(value)));
  }

  @Test(expected = IllegalStateException.class)
  public void getStatsBeforeInit() {
    configurationInstance.getStatistics();
  }

  @Test
  public void getStatistics() throws Exception {
    configurationInstance.initialise();
    assertThat(configurationInstance.getStatistics(), is(notNullValue()));
  }

  @Test
  public void getState() {
    assertThat(configurationInstance.getState(), is(sameInstance(configurationState)));
  }

  private class TestSimpleRetryPolicyTemplate extends SimpleRetryPolicyTemplate {

    public TestSimpleRetryPolicyTemplate(long frequency, int retryCount) {
      super(frequency, retryCount);
    }

    @Override
    protected void computeStats() {
      // Do not compute stats as there are no mule context associated in the test.
    }
  }
}
