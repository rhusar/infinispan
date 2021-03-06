package org.infinispan.integrationtests.spring.boot.session.embedded;

import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.integrationtests.spring.boot.session.configuration.WebConfig;
import org.infinispan.spring.embedded.provider.SpringEmbeddedCacheManagerFactoryBean;
import org.infinispan.spring.embedded.session.configuration.EnableInfinispanEmbeddedHttpSession;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableAutoConfiguration
@EnableInfinispanEmbeddedHttpSession
@Import(WebConfig.class)
public class EmbeddedProtostreamConfiguration {

   @Bean
   public SpringEmbeddedCacheManagerFactoryBean springCacheManager() {
      SpringEmbeddedCacheManagerFactoryBean cacheManagerFactoryBean = new SpringEmbeddedCacheManagerFactoryBean();
      cacheManagerFactoryBean.addCustomGlobalConfiguration(new GlobalConfigurationBuilder().defaultCacheName("sessions"));
      ConfigurationBuilder defaultCacheBuilder = new ConfigurationBuilder();
      defaultCacheBuilder.encoding().mediaType(MediaType.APPLICATION_PROTOSTREAM_TYPE);
      cacheManagerFactoryBean.addCustomCacheConfiguration(defaultCacheBuilder);
      return cacheManagerFactoryBean;
   }

}
