package org.infinispan.search.mapper.mapping.impl;

import org.hibernate.search.engine.mapper.mapping.building.spi.MappingPartialBuildState;
import org.hibernate.search.engine.mapper.mapping.spi.MappingImplementor;
import org.hibernate.search.mapper.pojo.mapping.spi.PojoMappingDelegate;
import org.infinispan.query.concurrent.FailureCounter;
import org.infinispan.query.impl.EntityLoaderFactory;
import org.infinispan.search.mapper.mapping.EntityConverter;
import org.infinispan.search.mapper.mapping.SearchMapping;
import org.infinispan.util.concurrent.BlockingManager;

public class InfinispanMappingPartialBuildState implements MappingPartialBuildState {

   private final PojoMappingDelegate mappingDelegate;
   private final InfinispanTypeContextContainer typeContextContainer;
   private final EntityLoaderFactory<?> entityLoader;
   private final EntityConverter entityConverter;
   private final BlockingManager blockingManager;
   private final FailureCounter failureCounter;
   private final int maxConcurrency;

   InfinispanMappingPartialBuildState(PojoMappingDelegate mappingDelegate,
                                      InfinispanTypeContextContainer typeContextContainer,
                                      EntityLoaderFactory<?> entityLoader,
                                      EntityConverter entityConverter,
                                      BlockingManager blockingManager,
                                      FailureCounter failureCounter, int maxConcurrency) {
      this.mappingDelegate = mappingDelegate;
      this.typeContextContainer = typeContextContainer;
      this.entityLoader = entityLoader;
      this.entityConverter = entityConverter;
      this.blockingManager = blockingManager;
      this.failureCounter = failureCounter;
      this.maxConcurrency = maxConcurrency;
   }

   @Override
   public void closeOnFailure() {
      mappingDelegate.close();
   }

   public MappingImplementor<SearchMapping> finalizeMapping() {
      InfinispanMapping mapping = new InfinispanMapping(mappingDelegate, typeContextContainer, entityLoader, entityConverter,
            blockingManager, failureCounter, maxConcurrency);
      mapping.start();
      return mapping;
   }
}
