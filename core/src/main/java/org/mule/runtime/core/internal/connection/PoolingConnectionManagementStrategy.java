/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.connection;

import static java.lang.Integer.min;
import static org.mule.runtime.api.config.PoolingProfile.INITIALISE_ALL;
import static org.mule.runtime.api.config.PoolingProfile.INITIALISE_NONE;
import static org.mule.runtime.api.config.PoolingProfile.INITIALISE_ONE;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.internal.connection.ConnectionUtils.logPoolStatus;

import org.mule.runtime.api.config.PoolingProfile;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionHandler;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.connection.PoolingListener;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.DefaultMuleException;
import org.mule.runtime.core.api.MuleContext;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ConnectionManagementStrategy} which returns connections obtained from a {@link #pool}
 *
 * @param <C> the generic type of the connections to be managed
 * @since 4.0
 */
final class PoolingConnectionManagementStrategy<C> extends ConnectionManagementStrategy<C> {

  private static final Logger LOGGER = LoggerFactory.getLogger(PoolingConnectionManagementStrategy.class);

  private final PoolingProfile poolingProfile;
  private final GenericObjectPool<C> pool;
  private final String poolId;
  private final PoolingListener<C> poolingListener;

  /**
   * Creates a new instance
   *
   * @param connectionProvider the {@link ConnectionProvider} used to manage the connections
   * @param poolingProfile     the {@link PoolingProfile} which configures the {@link #pool}
   * @param poolingListener    a {@link PoolingListener}
   * @param muleContext        the application's {@link MuleContext}
   */
  PoolingConnectionManagementStrategy(ConnectionProvider<C> connectionProvider, PoolingProfile poolingProfile,
                                      PoolingListener<C> poolingListener, MuleContext muleContext, String ownerConfigName) {
    super(connectionProvider, muleContext);
    this.poolingProfile = poolingProfile;
    this.poolingListener = poolingListener;
    this.poolId = ownerConfigName.concat("-").concat(generateId());
    this.pool = createPool(ownerConfigName);
  }

  /**
   * Returns a {@link ConnectionHandler} which wraps a connection obtained from the {@link #pool}
   *
   * @return a {@link ConnectionHandler}
   * @throws ConnectionException if the connection could not be obtained
   */
  @Override
  public ConnectionHandler<C> getConnectionHandler() throws ConnectionException {
    try {
      return new PoolingConnectionHandler<>(borrowConnection(), pool, poolId, poolingListener, connectionProvider);
    } catch (ConnectionException e) {
      throw e;
    } catch (NoSuchElementException e) {
      throw new ConnectionException("Connection pool is exhausted", e);
    } catch (Exception e) {
      throw new ConnectionException("An exception was found trying to obtain a connection: " + e.getMessage(), e);
    }
  }

  private C borrowConnection() throws Exception {
    C connection = pool.borrowObject();
    LOGGER.debug("Acquiring connection {} from the pool {}", connection.toString(), poolId);
    logPoolStatus(LOGGER, pool, poolId);
    try {
      poolingListener.onBorrow(connection);
    } catch (Exception e) {
      pool.invalidateObject(connection);
      throw e;
    }

    return connection;
  }

  /**
   * Closes the pool, causing the contained connections to be closed as well.
   *
   * @throws MuleException
   */
  // TODO: MULE-9082 - pool.close() doesn't destroy unreturned connections
  @Override
  public void close() throws MuleException {
    try {
      logPoolStatus(LOGGER, pool, poolId);
      LOGGER.debug("Closing pool {}", poolId);
      pool.close();
    } catch (Exception e) {
      throw new DefaultMuleException(createStaticMessage("Could not close connection pool"), e);
    }
  }

  private GenericObjectPool<C> createPool(String ownerConfigName) {
    GenericObjectPool.Config config = new GenericObjectPool.Config();
    config.maxIdle = poolingProfile.getMaxIdle();
    config.maxActive = poolingProfile.getMaxActive();
    config.maxWait = poolingProfile.getMaxWait();
    config.whenExhaustedAction = (byte) poolingProfile.getExhaustedAction();
    config.minEvictableIdleTimeMillis = poolingProfile.getMinEvictionMillis();
    config.timeBetweenEvictionRunsMillis = poolingProfile.getEvictionCheckIntervalMillis();
    GenericObjectPool genericPool = new GenericObjectPool(new ObjectFactoryAdapter(), config);
    LOGGER.debug("Creating pool with ID {} for config {}", poolId, ownerConfigName);

    applyInitialisationPolicy(genericPool);
    logPoolStatus(LOGGER, genericPool, poolId);

    return genericPool;
  }

  protected void applyInitialisationPolicy(GenericObjectPool pool) {
    int initialConnections;
    switch (poolingProfile.getInitialisationPolicy()) {
      case INITIALISE_NONE:
        initialConnections = 0;
        break;
      case INITIALISE_ONE:
        initialConnections = 1;
        break;
      case INITIALISE_ALL:
        if (poolingProfile.getMaxActive() < 0) {
          initialConnections = poolingProfile.getMaxIdle();
        } else if (poolingProfile.getMaxIdle() < 0) {
          initialConnections = poolingProfile.getMaxActive();
        } else {
          initialConnections = min(poolingProfile.getMaxActive(), poolingProfile.getMaxIdle());
        }
        break;
      default:
        throw new IllegalStateException("Unexpected value for pooling profile initialization policy: "
            + poolingProfile.getInitialisationPolicy());
    }

    LOGGER.debug("Initializing pool {} with {} initial connections", poolId, initialConnections);
    for (int t = 0; t < initialConnections; t++) {
      try {
        pool.addObject();
      } catch (Exception e) {
        LOGGER.warn("Failed to create a connection while applying the pool initialization policy.", e);
      }
    }
  }

  public PoolingProfile getPoolingProfile() {
    return poolingProfile;
  }

  private class ObjectFactoryAdapter implements PoolableObjectFactory<C> {

    @Override
    public C makeObject() throws Exception {
      C connection = connectionProvider.connect();
      LOGGER.debug("Created connection {}", connection.toString());
      return connection;
    }

    @Override
    public void destroyObject(C connection) throws Exception {
      LOGGER.debug("Disconnecting connection {}", connection.toString());
      connectionProvider.disconnect(connection);
    }

    @Override
    public boolean validateObject(C obj) {
      return false;
    }

    @Override
    public void activateObject(C connection) throws Exception {}

    @Override
    public void passivateObject(C connection) throws Exception {}
  }

  private String generateId() {
    return UUID.randomUUID().toString();
  }

}
