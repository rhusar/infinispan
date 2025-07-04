package org.infinispan.scripting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.infinispan.scripting.utils.ScriptingUtils.getScriptingManager;
import static org.infinispan.scripting.utils.ScriptingUtils.loadData;
import static org.infinispan.scripting.utils.ScriptingUtils.loadScript;
import static org.infinispan.test.TestingUtil.waitForNoRebalance;
import static org.infinispan.test.TestingUtil.withCacheManagers;
import static org.testng.AssertJUnit.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.infinispan.Cache;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.util.concurrent.CompletionStages;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.context.Flag;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.tasks.TaskContext;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.MultiCacheManagerCallable;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "scripting.ClusteredScriptingTest")
public class ClusteredScriptingTest extends AbstractInfinispanTest {

   private static final int EXPECTED_WORDS = 3202;

   @Test(dataProvider = "cacheModeProvider")
   public void testLocalScriptExecutionWithCache(final CacheMode cacheMode) {
      withCacheManagers(new MultiCacheManagerCallable(
            TestCacheManagerFactory.createCacheManager(cacheMode, false),
            TestCacheManagerFactory.createCacheManager(cacheMode, false)) {
         @Override
         public void call() throws IOException, ExecutionException, InterruptedException {
            ScriptingManager scriptingManager = getScriptingManager(cms[0]);
            Configuration configuration = new ConfigurationBuilder()
                  .encoding().key().mediaType(MediaType.APPLICATION_OBJECT_TYPE)
                  .encoding().value().mediaType(MediaType.APPLICATION_OBJECT_TYPE).build();
            for (EmbeddedCacheManager cm : cms) {
               cm.defineConfiguration(ScriptingTest.CACHE_NAME, configuration);
            }
            loadScript(scriptingManager, "/test.js");
            executeScriptOnManager("test.js", cms[0]);
            executeScriptOnManager("test.js", cms[1]);
         }
      });
   }

   @Test(dataProvider = "cacheModeProvider")
   public void testLocalScriptExecutionWithCache1(final CacheMode cacheMode) {
      withCacheManagers(new MultiCacheManagerCallable(TestCacheManagerFactory.createCacheManager(cacheMode, false),
            TestCacheManagerFactory.createCacheManager(cacheMode, false)) {

         @Override
         public void call() throws Exception {
            Configuration configuration = new ConfigurationBuilder()
                  .encoding().key().mediaType(MediaType.APPLICATION_OBJECT_TYPE)
                  .encoding().value().mediaType(MediaType.APPLICATION_OBJECT_TYPE).build();
            for (EmbeddedCacheManager cm : cms) {
               cm.defineConfiguration(ScriptingTest.CACHE_NAME, configuration);
            }
            Cache<Object, Object> cache = cms[0].getCache(ScriptingTest.CACHE_NAME);
            ScriptingManager scriptingManager = getScriptingManager(cms[0]);
            loadScript(scriptingManager, "/test1.js");

            cache.put("a", "newValue");

            executeScriptOnManager("test1.js", cms[0]);
            executeScriptOnManager("test1.js", cms[1]);
         }
      });
   }

   @Test(dataProvider = "cacheModeProvider")
   public void testDistExecScriptWithCache(final CacheMode cacheMode) {
      withCacheManagers(new MultiCacheManagerCallable(TestCacheManagerFactory.createCacheManager(cacheMode, false),
            TestCacheManagerFactory.createCacheManager(cacheMode, false)) {
         public void call() throws Exception {
            Cache cache1 = cms[0].getCache();
            Cache cache2 = cms[1].getCache();
            ScriptingManager scriptingManager = getScriptingManager(cms[0]);
            loadScript(scriptingManager, "/distExec1.js");
            waitForNoRebalance(cache1, cache2);

            CompletionStage<ArrayList<Address>> resultsFuture = scriptingManager.runScript("distExec1.js", new TaskContext().cache(cache1));
            ArrayList<Address> results = CompletionStages.join(resultsFuture);
            assertEquals(2, results.size());
            assertThat(results).containsExactlyInAnyOrder(cms[0].getAddress(), cms[1].getAddress());
         }
      });
   }

   @Test(dataProvider = "cacheModeProvider")
   public void testDistExecScriptWithCacheManagerAndParams(final CacheMode cacheMode) {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.clustering().cacheMode(cacheMode)
            .encoding().key().mediaType(MediaType.APPLICATION_OBJECT_TYPE)
            .encoding().value().mediaType(MediaType.APPLICATION_OBJECT_TYPE);

      withCacheManagers(new MultiCacheManagerCallable(TestCacheManagerFactory.createClusteredCacheManager(builder),
            TestCacheManagerFactory.createClusteredCacheManager(builder)) {
         public void call() throws Exception {
            Cache cache1 = cms[0].getCache();
            Cache cache2 = cms[1].getCache();

            ScriptingManager scriptingManager = getScriptingManager(cms[0]);
            loadScript(scriptingManager, "/distExec.js");
            waitForNoRebalance(cache1, cache2);

            CompletionStage<ArrayList<Address>> resultsFuture = scriptingManager.runScript("distExec.js",
                  new TaskContext().cache(cache1).addParameter("a", "value"));

            ArrayList<Address> results = CompletionStages.join(resultsFuture);
            assertEquals(2, results.size());
            assertThat(results).containsExactlyInAnyOrder(cms[0].getAddress(), cms[1].getAddress());

            assertEquals("value", cache1.get("a"));
            assertEquals("value", cache2.get("a"));
         }
      });
   }

   @Test(expectedExceptions = IllegalStateException.class, dataProvider = "cacheModeProvider", expectedExceptionsMessageRegExp = ".*without a cache binding.*")
   public void testDistributedScriptExecutionWithoutCacheBinding(final CacheMode cacheMode) {
      withCacheManagers(new MultiCacheManagerCallable(TestCacheManagerFactory.createCacheManager(cacheMode, false),
            TestCacheManagerFactory.createCacheManager(cacheMode, false)) {
         public void call() throws Exception {
            ScriptingManager scriptingManager = getScriptingManager(cms[0]);
            loadScript(scriptingManager, "/distExec.js");

            CompletionStages.join(scriptingManager.runScript("distExec.js"));
         }
      });
   }

   @Test(dataProvider = "cacheModeProvider")
   public void testDistributedMapReduceStreamWithFlag(final CacheMode cacheMode) {
      withCacheManagers(new MultiCacheManagerCallable(TestCacheManagerFactory.createCacheManager(cacheMode, false),
            TestCacheManagerFactory.createCacheManager(cacheMode, false)) {
         public void call() throws Exception {
            ScriptingManager scriptingManager = getScriptingManager(cms[0]);
            Cache cache1 = cms[0].getCache();
            Cache cache2 = cms[1].getCache();

            loadData(cache1, "/macbeth.txt");
            loadScript(scriptingManager, "/wordCountStream.js");
            waitForNoRebalance(cache1, cache2);

            Map<String, Long> resultsFuture = CompletionStages.join(scriptingManager.runScript(
                    "wordCountStream.js", new TaskContext().cache(cache1.getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL))));
            assertEquals(EXPECTED_WORDS, resultsFuture.size());
            assertEquals(resultsFuture.get("macbeth"), Long.valueOf(287));

            resultsFuture = CompletionStages.join(scriptingManager.runScript(
                    "wordCountStream.js", new TaskContext().cache(cache1.getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL))));
            assertEquals(EXPECTED_WORDS, resultsFuture.size());
            assertEquals(resultsFuture.get("macbeth"), Long.valueOf(287));
         }
      });
   }


   @Test(enabled = false, dataProvider = "cacheModeProvider", description = "Disabled due to ISPN-6173.")
   public void testDistributedMapReduceStreamLocalMode(final CacheMode cacheMode) throws IOException, ExecutionException, InterruptedException {
      withCacheManagers(new MultiCacheManagerCallable(TestCacheManagerFactory.createCacheManager(cacheMode, false),
            TestCacheManagerFactory.createCacheManager(cacheMode, false)) {
         public void call() throws Exception {
            ScriptingManager scriptingManager = getScriptingManager(cms[0]);
            Cache cache1 = cms[0].getCache();
            Cache cache2 = cms[1].getCache();

            loadData(cache1, "/macbeth.txt");
            loadScript(scriptingManager, "/wordCountStream_serializable.js");
            waitForNoRebalance(cache1, cache2);

            ArrayList<Map<String, Long>> resultsFuture = CompletionStages.join(scriptingManager.runScript(
                  "wordCountStream_serializable.js", new TaskContext().cache(cache1)));
            assertEquals(2, resultsFuture.size());
            assertEquals(EXPECTED_WORDS, resultsFuture.get(0).size());
            assertEquals(EXPECTED_WORDS, resultsFuture.get(1).size());
            assertEquals(resultsFuture.get(0).get("macbeth"), Long.valueOf(287));
            assertEquals(resultsFuture.get(1).get("macbeth"), Long.valueOf(287));
         }
      });
   }

   @Test(enabled = false, dataProvider = "cacheModeProvider", description = "Disabled due to ISPN-6173.")
   public void testDistributedMapReduceStreamLocalModeWithExecutors(final CacheMode cacheMode) throws IOException, ExecutionException, InterruptedException {
      withCacheManagers(new MultiCacheManagerCallable(TestCacheManagerFactory.createCacheManager(cacheMode, false),
            TestCacheManagerFactory.createCacheManager(cacheMode, false)) {
         public void call() throws Exception {
            ScriptingManager scriptingManager = getScriptingManager(cms[0]);
            Cache cache1 = cms[0].getCache();
            Cache cache2 = cms[1].getCache();

            loadData(cache1, "/macbeth.txt");
            loadScript(scriptingManager, "/wordCountStream_Exec.js");
            waitForNoRebalance(cache1, cache2);

            ArrayList<Map<String, Long>> resultsFuture = CompletionStages.join(scriptingManager.runScript(
                  "wordCountStream_Exec.js", new TaskContext().cache(cache1)));
            assertEquals(2, resultsFuture.size());
            assertEquals(EXPECTED_WORDS, resultsFuture.get(0).size());
            assertEquals(EXPECTED_WORDS, resultsFuture.get(1).size());
            assertEquals(resultsFuture.get(0).get("macbeth"), Long.valueOf(287));
            assertEquals(resultsFuture.get(1).get("macbeth"), Long.valueOf(287));
         }
      });
   }

   @Test(enabled = false, dataProvider = "cacheModeProvider", description = "Disabled due to ISPN-6173.")
   public void testDistributedMapReduceStream(final CacheMode cacheMode) throws IOException, ExecutionException, InterruptedException {
      withCacheManagers(new MultiCacheManagerCallable(TestCacheManagerFactory.createCacheManager(cacheMode, false),
            TestCacheManagerFactory.createCacheManager(cacheMode, false)) {
         public void call() throws Exception {
            ScriptingManager scriptingManager = getScriptingManager(cms[0]);
            Cache cache1 = cms[0].getCache();
            Cache cache2 = cms[1].getCache();

            loadData(cache1, "/macbeth.txt");
            loadScript(scriptingManager, "/wordCountStream_dist.js");
            waitForNoRebalance(cache1, cache2);

            ArrayList<Map<String, Long>> resultsFuture = CompletionStages.join(scriptingManager.runScript(
                  "wordCountStream_dist.js", new TaskContext().cache(cache1)));
            assertEquals(2, resultsFuture.size());
            assertEquals(EXPECTED_WORDS, resultsFuture.get(0).size());
            assertEquals(EXPECTED_WORDS, resultsFuture.get(1).size());
            assertEquals(resultsFuture.get(0).get("macbeth"), Long.valueOf(287));
            assertEquals(resultsFuture.get(1).get("macbeth"), Long.valueOf(287));
         }
      });
   }

   private void executeScriptOnManager(String scriptName, EmbeddedCacheManager cacheManager) throws InterruptedException, ExecutionException {
      ScriptingManager scriptingManager = getScriptingManager(cacheManager);
      String value = CompletionStages.join(scriptingManager.runScript(scriptName, new TaskContext().addParameter("a", "value")));
      assertEquals(value, cacheManager.getCache(ScriptingTest.CACHE_NAME).get("a"));
   }

   @DataProvider(name = "cacheModeProvider")
   private static Object[][] provideCacheMode() {
      return new Object[][]{{CacheMode.REPL_SYNC}, {CacheMode.DIST_SYNC}};
   }
}
