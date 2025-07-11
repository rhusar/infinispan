package org.infinispan.server.functional.rest;

import static org.infinispan.client.rest.RestResponse.NOT_FOUND;
import static org.infinispan.client.rest.RestResponse.OK;
import static org.infinispan.server.test.core.Common.assertStatus;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Function;

import org.infinispan.client.rest.RestClient;
import org.infinispan.client.rest.configuration.RestClientConfigurationBuilder;
import org.infinispan.server.functional.ClusteredIT;
import org.infinispan.server.test.api.TestClientDriver;
import org.infinispan.server.test.junit5.InfinispanServer;
import org.junit.jupiter.api.Test;

/**
 * @since 10.0
 */
public class RestRouter {

   @InfinispanServer(ClusteredIT.class)
   public static TestClientDriver SERVERS;

   @Test
   public void testRestRouting() throws Exception {
      Function<String, RestClient> client = c -> SERVERS.rest()
            .withClientConfiguration(new RestClientConfigurationBuilder().contextPath(c))
            .get();

      try (RestClient restCtx = client.apply("/rest");
           RestClient invalidCtx = client.apply("/invalid");
           RestClient emptyCtx = client.apply("/")) {

         String body = assertStatus(OK, restCtx.server().info());
         assertTrue(body.contains("version"), body);

         assertStatus(NOT_FOUND, emptyCtx.server().info());

         assertStatus(NOT_FOUND, invalidCtx.server().info());
      }
   }
}
