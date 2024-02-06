package org.infinispan.client.hotrod.impl.protocol;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.infinispan.client.hotrod.DataFormat;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.impl.ClientTopology;

/**
 * Hot Rod request header parameters
 *
 * @author Galder Zamarreño
 * @since 5.1
 */
public class HeaderParams {
   final short opCode;
   final short opRespCode;
   byte[] cacheName;
   int flags;
   final byte txMarker;
   final AtomicReference<ClientTopology> clientTopology;
   final long messageId;
   int topologyAge;
   final DataFormat dataFormat;
   Map<String, byte[]> otherParams;
   // sent client intelligence: to read the response
   volatile byte clientIntelligence;

   public HeaderParams(short requestCode, short responseCode, int flags, byte txMarker, long messageId, DataFormat dataFormat, AtomicReference<ClientTopology> clientTopology) {
      opCode = requestCode;
      opRespCode = responseCode;
      this.flags = flags;
      this.txMarker = txMarker;
      this.messageId = messageId;
      this.dataFormat = dataFormat;
      this.clientTopology = clientTopology;
   }

   public HeaderParams cacheName(byte[] cacheName) {
      this.cacheName = cacheName;
      return this;
   }

   public long messageId() {
      return messageId;
   }

   public HeaderParams topologyAge(int topologyAge) {
      this.topologyAge = topologyAge;
      return this;
   }

   public DataFormat dataFormat() {
      return dataFormat;
   }

   public byte[] cacheName() {
      return cacheName;
   }

   public void otherParam(String paramKey, byte[] paramValue) {
      if (otherParams == null) {
         otherParams = new HashMap<>(2);
      }
      otherParams.put(paramKey, paramValue);
   }

   public Map<String, byte[]> otherParams() {
      return otherParams;
   }

   public int flags() {
      return flags;
   }

   public void addFlags(Flag ... flags) {
      if (flags == null) return;

      for (Flag flag : flags) {
         if (flag == null) continue;

         this.flags |= flag.getFlagInt();
      }
   }

   public AtomicReference<ClientTopology> getClientTopology() {
      return clientTopology;
   }
}
