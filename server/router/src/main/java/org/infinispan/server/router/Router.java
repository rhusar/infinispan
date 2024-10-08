package org.infinispan.server.router;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.infinispan.server.router.configuration.RouterConfiguration;
import org.infinispan.server.router.logging.Log;
import org.infinispan.server.router.router.EndpointRouter;
import org.infinispan.server.router.router.impl.hotrod.HotRodEndpointRouter;
import org.infinispan.server.router.router.impl.rest.RestEndpointRouter;
import org.infinispan.server.router.router.impl.singleport.SinglePortEndpointRouter;

/**
 * The main entry point for the router.
 *
 * @author Sebastian Łaskawiec
 */
public class Router {
   private final RouterConfiguration routerConfiguration;
   private final Set<EndpointRouter> endpointRouters = new HashSet<>();

   /**
    * Creates new {@link Router} based on {@link RouterConfiguration}.
    *
    * @param routerConfiguration {@link RouterConfiguration} object.
    */
   public Router(RouterConfiguration routerConfiguration) {
      this.routerConfiguration = routerConfiguration;
      if (routerConfiguration.hotRodRouter() != null) {
         endpointRouters.add(new HotRodEndpointRouter(routerConfiguration.hotRodRouter()));
      }
      if (routerConfiguration.restRouter() != null) {
         endpointRouters.add(new RestEndpointRouter(routerConfiguration.restRouter()));
      }
      if (routerConfiguration.singlePortRouter() != null) {
         endpointRouters.add(new SinglePortEndpointRouter(routerConfiguration.singlePortRouter()));
      }
   }

   /**
    * Starts the router.
    */
   public void start() {
      endpointRouters.forEach(r -> r.start(routerConfiguration.routingTable(), null));
      Log.SERVER.printOutRoutingTable(routerConfiguration.routingTable());
   }

   /**
    * Stops the router.
    */
   public void stop() {
      endpointRouters.forEach(r -> r.stop());
   }

   /**
    * Gets internal {@link EndpointRouter} implementation for given protocol.
    *
    * @param protocol Protocol for obtaining the router.
    * @return The {@link EndpointRouter} implementation.
    */
   public Optional<EndpointRouter> getRouter(EndpointRouter.Protocol protocol) {
      return endpointRouters.stream().filter(r -> r.getProtocol() == protocol).findFirst();
   }
}
