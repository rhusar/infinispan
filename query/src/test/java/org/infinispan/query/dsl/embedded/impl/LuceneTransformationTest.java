package org.infinispan.query.dsl.embedded.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.infinispan.query.helper.IndexAccessor.extractSort;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.util.common.SearchException;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.objectfilter.ParsingException;
import org.infinispan.objectfilter.impl.ql.Function;
import org.infinispan.objectfilter.impl.ql.PropertyPath;
import org.infinispan.objectfilter.impl.syntax.parser.FunctionPropertyPath;
import org.infinispan.objectfilter.impl.syntax.parser.IckleParser;
import org.infinispan.objectfilter.impl.syntax.parser.IckleParsingResult;
import org.infinispan.objectfilter.impl.syntax.parser.ReflectionEntityNamesResolver;
import org.infinispan.query.dsl.embedded.impl.model.Employee;
import org.infinispan.query.helper.SearchMappingHelper;
import org.infinispan.search.mapper.mapping.SearchMapping;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.util.concurrent.BlockingManager;
import org.testng.annotations.Test;

/**
 * Test the parsing and transformation of Ickle queries to Lucene queries.
 *
 * @author anistor@redhat.com
 * @since 9.0
 */
@Test(groups = "functional", testName = "query.dsl.embedded.impl.LuceneTransformationTest")
public class LuceneTransformationTest extends SingleCacheManagerTest {

   private SearchMapping searchMapping;
   private HibernateSearchPropertyHelper propertyHelper;

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createCacheManager();
      BlockingManager blockingManager = GlobalComponentRegistry.componentOf(cacheManager, BlockingManager.class);

      // the cache manager is created only to provide manager instances to the search mapping
      searchMapping = SearchMappingHelper.createSearchMappingForTests(blockingManager, Employee.class);
      propertyHelper = new HibernateSearchPropertyHelper(searchMapping, new ReflectionEntityNamesResolver(null));

      return cacheManager;
   }

   @Override
   protected void teardown() {
      if (searchMapping != null) {
         searchMapping.close();
      }
      super.teardown();
   }

   @Test(expectedExceptions = {ParsingException.class},
         expectedExceptionsMessageRegExp = "ISPN028502: Unknown alias: a.")
   public void testRaiseExceptionDueToUnknownAlias() {
      parseAndTransform("from org.infinispan.query.dsl.embedded.impl.model.Employee e where a.name = 'same'");
   }

   @Test
   public void testMatchAllQuery() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee",
            "+*:* #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e",
            "+*:* #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e",
            "+*:* #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where true",
            "+*:* #__HSEARCH_type:main");
   }

   @Test
   public void testRejectAllQuery() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where false",
            "+(-*:* #*:*) #__HSEARCH_type:main");   //TODO [anistor] maybe this should be "-*:*"
   }

   @Test
   public void testFullTextKeyword() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : 'bicycle'",
            "+text:bicycle #__HSEARCH_type:main");
   }

   @Test
   public void testFullTextWildcard() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : 'geo*e'",
            "+text:geo*e #__HSEARCH_type:main");
   }

   @Test
   public void testFullTextFuzzy() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : 'jonh'~2",
            "+text:jonh~2 #__HSEARCH_type:main");
   }

   @Test
   public void testFullTextPhrase() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : 'twisted cables'",
            "+text:\"twisted cables\" #__HSEARCH_type:main");
   }

   @Test
   public void testFullTextRegexp() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : /te?t/",
            "+text:/te?t/ #__HSEARCH_type:main");
   }

   @Test
   public void testFullTextRegexp_boosted() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : /te?t/^3",
            "+(text:/te?t/)^3.0 #__HSEARCH_type:main");
   }

   @Test
   public void testFullTextRange() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : ['AAA' 'ZZZ']",
            "+text:[aaa TO zzz] #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : ['AAA' to 'ZZZ']",
            "+text:[aaa TO zzz] #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : [* to 'eeee']",
            "+text:[* TO eeee] #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : ['eeee' to *]",
            "+text:[eeee TO *] #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : [* *]",
            "+ConstantScore(FieldExistsQuery [field=text]) #__HSEARCH_type:main");
   }

   @Test
   public void testFullTextFieldBoost() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : 'foo'^3.0'",
            "+(text:foo)^3.0 #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : ('foo')^3.0'",
            "+(text:foo)^3.0 #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : ('foo' and 'bar')^3.0'",
            "+(+text:foo +text:bar)^3.0 #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : 'foo'^3.0 || e.analyzedInfo : 'bar'",
            "+((text:foo)^3.0 analyzedInfo:bar) #__HSEARCH_type:main");
   }

   @Test
   public void testFullTextFieldOccur() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : (-'foo') ",
            "+(-text:foo #*:*) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : (-'foo' +'bar')",
            "+((-text:foo #*:*) text:bar) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where not e.text : (-'foo' +'bar')",
            "+(-((-text:foo #*:*) text:bar) #*:*) #__HSEARCH_type:main");

// TODO [anistor] fix this
//      assertGeneratedLuceneQuery(
//            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where !e.text : (-'foo' +'bar')",
//            "-((-text:foo) (+text:bar)) #*:*");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text : (-'foo' '*')",
            "+((-text:foo #*:*) *:*) #__HSEARCH_type:main");
   }

   @Test
   public void testRestrictedQueryUsingSelect() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = 'same' or e.id = 5",
            "+(name:same id:5) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = 'same' and e.id = 5",
            "+(+name:same +id:5) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = 'same' and not e.id = 5",
            "+(+name:same -id:5) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = 'same' or not e.id = 5",
            "+(name:same (-id:5 #*:*)) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where not e.id = 5",
            "+(-id:5 #*:*) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.id != 5",
            "+(-id:5 #*:*) #__HSEARCH_type:main");
   }

   @Test
   public void testFieldMapping() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.someMoreInfo = 'foo'",
            "+someMoreInfo:foo #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.sameInfo = 'foo'",
            "+sameInfo:foo #__HSEARCH_type:main");
   }

   @Test(expectedExceptions = {SearchException.class},
         expectedExceptionsMessageRegExp = "HSEARCH000610: Unknown field 'otherInfo'.*Context: indexes \\[org.infinispan.query.dsl.embedded.impl.model.Employee\\]")
   public void testWrongFieldName() {
      parseAndTransform("from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.otherInfo = 'foo'");
   }

   @Test
   public void testProjectionQuery() {
      String ickle = "select e.id, e.name from org.infinispan.query.dsl.embedded.impl.model.Employee e";
      IckleParsingResult<Class<?>> parsed = parse(ickle);
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString()).isEqualTo("+*:* #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).isEqualTo(new String[]{"id", "name"});
   }

   @Test
   public void testEmbeddedProjectionQuery() {
      String ickle = "select e.author.name from org.infinispan.query.dsl.embedded.impl.model.Employee e";
      IckleParsingResult<Class<?>> parsed = parse(ickle);
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString()).isEqualTo("+*:* #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).isEqualTo(new String[]{"author.name"});
   }

   @Test
   public void testNestedEmbeddedProjectionQuery() {
      String ickle = "select e.author.address.street from org.infinispan.query.dsl.embedded.impl.model.Employee e";
      IckleParsingResult<Class<?>> parsed = parse(ickle);
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString()).isEqualTo("+*:* #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).isEqualTo(new String[]{"author.address.street"});
   }

   @Test
   public void testQueryWithUnqualifiedPropertyReferences() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where name = 'same' and not id = 5",
            "+(+name:same -id:5) #__HSEARCH_type:main");
   }

   @Test
   public void testNegatedQuery() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where NOT e.name = 'same'",
            "+(-name:same #*:*) #__HSEARCH_type:main");

      // JPQL syntax
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name <> 'same'",
            "+(-name:same #*:*) #__HSEARCH_type:main");

      // HQL syntax
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name != 'same'",
            "+(-name:same #*:*) #__HSEARCH_type:main");
   }

   @Test
   public void testNegatedQueryOnNumericProperty() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.position <> 3",
            "+(-position:[3 TO 3] #*:*) #__HSEARCH_type:main");
   }

   @Test
   public void testNegatedRangeQuery() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee where name = 'Bob' and not position between 1 and 3",
            "+(+name:Bob -position:[1 TO 3]) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = 'Bob' and not e.position between 1 and 3",
            "+(+name:Bob -position:[1 TO 3]) #__HSEARCH_type:main");
   }

   @Test
   public void testQueryWithNamedParameter() {
      Map<String, Object> namedParameters = new HashMap<>();
      namedParameters.put("nameParameter", "Bob");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee where name = :nameParameter",
            namedParameters,
            "+name:Bob #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = :nameParameter",
            namedParameters,
            "+name:Bob #__HSEARCH_type:main");
   }

   @Test
   public void testBooleanQuery() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = 'same' or (e.id = 4 and e.name = 'Bob')",
            "+(name:same (+id:4 +name:Bob)) #__HSEARCH_type:main");
   }

   @Test
   public void testBooleanQueryUsingSelect() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = 'same' or (e.id = 4 and e.name = 'Bob')",
            "+(name:same (+id:4 +name:Bob)) #__HSEARCH_type:main");
   }

   @Test
   public void testBetweenQuery() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name between 'aaa' and 'zzz'",
            "+name:[aaa TO zzz] #__HSEARCH_type:main");
   }

   @Test
   public void testNotBetweenQuery() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name not between 'aaa' and 'zzz'",
            "+(-name:[aaa TO zzz] #*:*) #__HSEARCH_type:main");
   }

   @Test
   public void testNumericNotBetweenQuery() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where not e.position between 1 and 3",
            "+(-position:[1 TO 3] #*:*) #__HSEARCH_type:main");
   }

   @Test
   public void testBetweenQueryForCharacterLiterals() {
      assertGeneratedLuceneQuery("select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name between 'a' and 'z'", "+name:[a TO z] #__HSEARCH_type:main");
   }

   @Test
   public void testBetweenQueryWithNamedParameters() {
      Map<String, Object> namedParameters = new HashMap<>();
      namedParameters.put("p1", "aaa");
      namedParameters.put("p2", "zzz");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name between :p1 and :p2",
            namedParameters,
            "+name:[aaa TO zzz] #__HSEARCH_type:main");
   }

   @Test
   public void testNumericBetweenQuery() {
      Map<String, Object> namedParameters = new HashMap<>();
      namedParameters.put("p1", 10L);
      namedParameters.put("p2", 20L);

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.position between :p1 and :p2",
            namedParameters,
            "+position:[10 TO 20] #__HSEARCH_type:main");
   }

   @Test
   public void testQueryWithEmbeddedPropertyInFromClause() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.author.name = 'Bob'",
            "+author.name:Bob #__HSEARCH_type:main");
   }

   @Test
   public void testLessThanQuery() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.position < 100",
            "+position:[-9223372036854775808 TO 99] #__HSEARCH_type:main");
   }

   @Test
   public void testLessThanOrEqualsToQuery() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.position <= 100",
            "+position:[-9223372036854775808 TO 100] #__HSEARCH_type:main");
   }

   @Test
   public void testGreaterThanOrEqualsToQuery() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee where position >= 100",
            "+position:[100 TO 9223372036854775807] #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.position >= 100",
            "+position:[100 TO 9223372036854775807] #__HSEARCH_type:main");
   }

   @Test
   public void testGreaterThanQuery() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee where position > 100",
            "+position:[101 TO 9223372036854775807] #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.position > 100",
            "+position:[101 TO 9223372036854775807] #__HSEARCH_type:main");
   }

   @Test
   public void testInQuery() {
      assertGeneratedLuceneQuery(
            "from org.infinispan.query.dsl.embedded.impl.model.Employee where name in ('Bob', 'Alice')",
            "+(name:Bob name:Alice) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name in ('Bob', 'Alice')",
            "+(name:Bob name:Alice) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.position in (10, 20, 30, 40)",
            "+(position:[10 TO 10] position:[20 TO 20] position:[30 TO 30] position:[40 TO 40]) #__HSEARCH_type:main");
   }

   @Test
   public void testInQueryWithNamedParameters() {
      Map<String, Object> namedParameters = new HashMap<>();
      namedParameters.put("name1", "Bob");
      namedParameters.put("name2", "Alice");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name in (:name1, :name2)",
            namedParameters,
            "+(name:Bob name:Alice) #__HSEARCH_type:main");

      namedParameters = new HashMap<>();
      namedParameters.put("pos1", 10);
      namedParameters.put("pos2", 20);
      namedParameters.put("pos3", 30);
      namedParameters.put("pos4", 40);

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.position in (:pos1, :pos2, :pos3, :pos4)",
            namedParameters,
            "+(position:[10 TO 10] position:[20 TO 20] position:[30 TO 30] position:[40 TO 40]) #__HSEARCH_type:main");
   }

   @Test
   public void testNotInQuery() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name not in ('Bob', 'Alice')",
            "+(-(name:Bob name:Alice) #*:*) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.position not in (10, 20, 30, 40)",
            "+(-(position:[10 TO 10] position:[20 TO 20] position:[30 TO 30] position:[40 TO 40]) #*:*) #__HSEARCH_type:main");
   }

   @Test
   public void testLikeQuery() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name LIKE 'Al_ce'",
            "+name:Al?ce #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name LIKE 'Ali%'",
            "+name:Ali* #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name LIKE 'Ali%%'",
            "+name:Ali** #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name LIKE '_l_ce'",
            "+name:?l?ce #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name LIKE '___ce'",
            "+name:???ce #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name LIKE '_%_ce'",
            "+name:?*?ce #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name LIKE 'Alice in wonderl%'",
            "+name:Alice in wonderl* #__HSEARCH_type:main");
   }

   @Test
   public void testNotLikeQuery() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name NOT LIKE 'Al_ce'",
            "+(-name:Al?ce #*:*) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name NOT LIKE 'Ali%'",
            "+(-name:Ali* #*:*) #__HSEARCH_type:main");

      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name NOT LIKE '_l_ce' and not (e.title LIKE '%goo' and e.position = '5')",
            "+(-name:?l?ce -(+title:*goo +position:[5 TO 5]) #*:*) #__HSEARCH_type:main");
   }

   @Test
   public void testIsNullQuery() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name IS null",
            "+(-ConstantScore(FieldExistsQuery [field=name]) #*:*) #__HSEARCH_type:main");
   }

   @Test
   public void testIsNullQueryForEmbeddedEntity() {
      // Meta-field added as part of HSEARCH-3905
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.author IS null",
            "+(-((ConstantScore(FieldExistsQuery [field=author.address.city]) ConstantScore(FieldExistsQuery [field=author.address.street]) __HSEARCH_field_names:author.address) ConstantScore(FieldExistsQuery [field=author.name]) ConstantScore(FieldExistsQuery [field=author.vatNumber]) __HSEARCH_field_names:author) #*:*) #__HSEARCH_type:main");
   }

   @Test
   public void testIsNotNullQuery() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name IS NOT null",
            "+ConstantScore(FieldExistsQuery [field=name]) #__HSEARCH_type:main");
   }

   @Test
   public void testCollectionOfEmbeddableQuery() {
      assertGeneratedLuceneQuery(
            "select e from org.infinispan.query.dsl.embedded.impl.model.Employee e JOIN e.contactDetails d WHERE d.email = 'ninja647@mailinator.com' ",
            "+contactDetails.email:ninja647@mailinator.com #__HSEARCH_type:main");
   }

   @Test
   public void testCollectionOfEmbeddableInEmbeddedQuery() {
      assertGeneratedLuceneQuery(
            "SELECT e FROM org.infinispan.query.dsl.embedded.impl.model.Employee e "
                  + " JOIN e.contactDetails d"
                  + " JOIN d.address.alternatives as a "
                  + "WHERE a.postCode = '90210' ",
            "+contactDetails.address.alternatives.postCode:90210 #__HSEARCH_type:main");
   }

   @Test(expectedExceptions = ParsingException.class,
         expectedExceptionsMessageRegExp = "ISPN028501: The type org.infinispan.query.dsl.embedded.impl.model.Employee does not have an accessible property named 'foobar'.")
   public void testRaiseExceptionDueToUnknownQualifiedProperty() {
      parseAndTransform("from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.foobar = 'same'");
   }

   @Test(expectedExceptions = ParsingException.class,
         expectedExceptionsMessageRegExp = "ISPN028501: The type org.infinispan.query.dsl.embedded.impl.model.Employee does not have an accessible property named 'foobar'.")
   public void testRaiseExceptionDueToUnknownUnqualifiedProperty() {
      parseAndTransform("from org.infinispan.query.dsl.embedded.impl.model.Employee e where foobar = 'same'");
   }

   @Test(expectedExceptions = ParsingException.class,
         expectedExceptionsMessageRegExp = "ISPN028522: No relational queries can be applied to property 'text' in type org.infinispan.query.dsl.embedded.impl.model.Employee since the property is analyzed.")
   public void testRaiseExceptionDueToAnalyzedPropertyInFromClause() {
      parseAndTransform("from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.text = 'foo'");
   }

   @Test(expectedExceptions = ParsingException.class,
         expectedExceptionsMessageRegExp = "ISPN028501: The type org.infinispan.query.dsl.embedded.impl.model.Employee does not have an accessible property named 'foobar'.")
   public void testRaiseExceptionDueToUnknownPropertyInSelectClause() {
      parseAndTransform("select e.foobar from org.infinispan.query.dsl.embedded.impl.model.Employee e");
   }

   @Test(expectedExceptions = ParsingException.class,
         expectedExceptionsMessageRegExp = "ISPN028501: The type org.infinispan.query.dsl.embedded.impl.model.Employee does not have an accessible property named 'foo'.")
   public void testRaiseExceptionDueToUnknownPropertyInEmbeddedSelectClause() {
      parseAndTransform("select e.author.foo from org.infinispan.query.dsl.embedded.impl.model.Employee e");
   }

   @Test(expectedExceptions = ParsingException.class,
         expectedExceptionsMessageRegExp = "ISPN028503: Property author can not be selected from type org.infinispan.query.dsl.embedded.impl.model.Employee since it is an embedded entity.")
   public void testRaiseExceptionDueToSelectionOfCompleteEmbeddedEntity() {
      parseAndTransform("select e.author from org.infinispan.query.dsl.embedded.impl.model.Employee e");
   }

   @Test(expectedExceptions = ParsingException.class,
         expectedExceptionsMessageRegExp = "ISPN028503: Property author can not be selected from type org.infinispan.query.dsl.embedded.impl.model.Employee since it is an embedded entity.")
   public void testRaiseExceptionDueToUnqualifiedSelectionOfCompleteEmbeddedEntity() {
      parseAndTransform("select author from org.infinispan.query.dsl.embedded.impl.model.Employee e");
   }

   @Test
   public void testDetermineTargetEntityType() {
      IckleParsingResult<Class<?>> parsed = parse("select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = 'same' and not e.id = 5");
      assertThat(parsed.getTargetEntityMetadata()).isSameAs(Employee.class);
      assertThat(parsed.getTargetEntityName()).isEqualTo("org.infinispan.query.dsl.embedded.impl.model.Employee");

      parsed = parse("select e from org.infinispan.query.dsl.embedded.impl.model.Employee e");
      assertThat(parsed.getTargetEntityMetadata()).isSameAs(Employee.class);
      assertThat(parsed.getTargetEntityName()).isEqualTo("org.infinispan.query.dsl.embedded.impl.model.Employee");
   }

   @Test
   public void testBuildOneFieldSort() {
      SearchQuery<?> result = parseAndTransform("select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = 'same' order by e.title");
      Sort sort = extractSort(result);
      assertThat(sort).isNotNull();
      assertThat(sort.getSort().length).isEqualTo(1);
      assertThat(sort.getSort()[0].getField()).isEqualTo("title");
      assertThat(sort.getSort()[0].getReverse()).isEqualTo(false);
      assertThat(sort.getSort()[0].getType()).isEqualTo(SortField.Type.CUSTOM);
   }

   @Test
   public void testBuildTwoFieldsSort() {
      SearchQuery<?> result = parseAndTransform("select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = 'same' order by e.title, e.position DESC");
      Sort sort = extractSort(result);
      assertThat(sort).isNotNull();
      assertThat(sort.getSort().length).isEqualTo(2);
      assertThat(sort.getSort()[0].getField()).isEqualTo("title");
      assertThat(sort.getSort()[0].getReverse()).isEqualTo(false);
      assertThat(sort.getSort()[0].getType()).isEqualTo(SortField.Type.CUSTOM);
      assertThat(sort.getSort()[1].getField()).isEqualTo("position");
      assertThat(sort.getSort()[1].getReverse()).isEqualTo(true);
      assertThat(sort.getSort()[1].getType()).isEqualTo(SortField.Type.CUSTOM);
   }

   @Test
   public void testBuildSortForNullEncoding() {
      SearchQuery<?> result = parseAndTransform("select e from org.infinispan.query.dsl.embedded.impl.model.Employee e order by e.code DESC");
      Sort sort = extractSort(result);
      assertThat(sort).isNotNull();
      assertThat(sort.getSort().length).isEqualTo(1);
      assertThat(sort.getSort()[0].getField()).isEqualTo("code");
      assertThat(sort.getSort()[0].getType()).isEqualTo(SortField.Type.CUSTOM);
   }

   @Test(expectedExceptions = ParsingException.class,
         expectedExceptionsMessageRegExp = "ISPN028526: Invalid query: select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = 'same' order by e.title DESblah, e.name ASC.*")
   public void testRaiseExceptionDueToUnrecognizedSortDirection() {
      parseAndTransform("select e from org.infinispan.query.dsl.embedded.impl.model.Employee e where e.name = 'same' order by e.title DESblah, e.name ASC");
   }

   @Test
   public void testBeAbleToJoinOnCollectionOfEmbedded() {
      String ickle = "select d.email from org.infinispan.query.dsl.embedded.impl.model.Employee e JOIN e.contactDetails d";

      SearchQuery<?> result = parseAndTransform(ickle);
      assertThat(result.queryString()).isEqualTo("+*:* #__HSEARCH_type:main");

      assertThat(parse(ickle).getProjections()).containsOnly("contactDetails.email");
   }

   @Test
   public void testBeAbleToJoinOnCollectionOfEmbeddedWithEmbedded() {
      String ickle = "select d.email from org.infinispan.query.dsl.embedded.impl.model.Employee e JOIN e.contactDetails d WHERE d.address.postCode='EA123'";
      IckleParsingResult<Class<?>> parsed = parse(ickle);
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString()).isEqualTo("+contactDetails.address.postCode:EA123 #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsOnly("contactDetails.email");
   }

   @Test
   public void testBeAbleToJoinOnCollectionOfEmbeddedWithEmbeddedAndUseInOperator() {
      String ickle = "select d.email from org.infinispan.query.dsl.embedded.impl.model.Employee e JOIN e.contactDetails d WHERE d.address.postCode IN ('EA123')";
      IckleParsingResult<Class<?>> parsed = parse(ickle);
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString()).isEqualTo("+contactDetails.address.postCode:EA123 #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsOnly("contactDetails.email");
   }

   @Test
   public void testBeAbleToJoinOnCollectionOfEmbeddedWithEmbeddedAndUseBetweenOperator() {
      String ickle = "select d.email from org.infinispan.query.dsl.embedded.impl.model.Employee e JOIN e.contactDetails d WHERE d.address.postCode BETWEEN '0000' AND 'ZZZZ'";
      IckleParsingResult<Class<?>> parsed = parse(ickle);
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString()).isEqualTo("+contactDetails.address.postCode:[0000 TO ZZZZ] #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsOnly("contactDetails.email");
   }

   @Test
   public void testBeAbleToJoinOnCollectionOfEmbeddedWithEmbeddedAndUseGreaterOperator() {
      String ickle = "select d.email from org.infinispan.query.dsl.embedded.impl.model.Employee e JOIN e.contactDetails d WHERE d.address.postCode > '0000'";
      IckleParsingResult<Class<?>> parsed = parse(ickle);
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString()).isEqualTo("+contactDetails.address.postCode:{0000 TO *] #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsOnly("contactDetails.email");
   }

   @Test
   public void testBeAbleToJoinOnCollectionOfEmbeddedWithEmbeddedAndUseLikeOperator() {
      String ickle = "select d.email from org.infinispan.query.dsl.embedded.impl.model.Employee e JOIN e.contactDetails d WHERE d.address.postCode LIKE 'EA1%'";
      IckleParsingResult<Class<?>> parsed = parse(ickle);
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString()).isEqualTo("+contactDetails.address.postCode:EA1* #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsOnly("contactDetails.email");
   }

   @Test
   public void testJoinWithFilterOnCollectionOfNestedEmbedded() {
      String ickle = "select d.email from org.infinispan.query.dsl.embedded.impl.model.Employee e " +
            "JOIN e.nestedContactDetails d WHERE d.email ='1234' AND d.phoneNumber='+36 12 345-678'";

      IckleParsingResult<Class<?>> ickleParsingResult = parse(ickle);
      SearchQuery<?> result = transform(ickleParsingResult);
      assertThat(result.queryString()).isEqualTo("+ToParentBlockJoinQuery (#__HSEARCH_type:child #__HSEARCH_nested_document_path:nestedContactDetails " +
            "+(+nestedContactDetails.email:1234 +nestedContactDetails.phoneNumber:+36 12 345-678)) #__HSEARCH_type:main");
   }

   @Test
   public void testJoinWithFilterOnSingleNestedEmbedded() {
      String ickle = "select e.name from org.infinispan.query.dsl.embedded.impl.model.Employee e " +
            "JOIN e.nestedAuthor auth " +
            "WHERE auth.name='DummyAuthor' AND auth.vatNumber='12345AZ'";

      IckleParsingResult<Class<?>> ickleParsingResult = parse(ickle);
      SearchQuery<?> result = transform(ickleParsingResult);
      assertThat(result.queryString()).isEqualTo("+ToParentBlockJoinQuery (#__HSEARCH_type:child #__HSEARCH_nested_document_path:nestedAuthor " +
            "+(+nestedAuthor.name:DummyAuthor +nestedAuthor.vatNumber:12345AZ)) #__HSEARCH_type:main");
   }

   @Test
   public void testBeAbleToProjectUnqualifiedField() {
      String ickle = "SELECT name, text FROM org.infinispan.query.dsl.embedded.impl.model.Employee e JOIN e.contactDetails d";
      IckleParsingResult<Class<?>> parsed = parse(ickle);
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString()).isEqualTo("+*:* #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsOnly("name", "text");
   }

   @Test
   public void testBeAbleToProjectUnqualifiedFieldAndQualifiedField() {
      String ickle = "SELECT name, text, d.email FROM org.infinispan.query.dsl.embedded.impl.model.Employee e JOIN e.contactDetails d";
      IckleParsingResult<Class<?>> parsed = parse(ickle);
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString()).isEqualTo("+*:* #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsOnly("name", "text", "contactDetails.email");
   }

   @Test
   public void testBeAbleToProjectQualifiedField() {
      String ickle = "SELECT e.name, e.text, d.email FROM org.infinispan.query.dsl.embedded.impl.model.Employee e JOIN e.contactDetails d";
      IckleParsingResult<Class<?>> parsed = parse(ickle);
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString()).isEqualTo("+*:* #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsOnly("name", "text", "contactDetails.email");
   }

   @Test
   public void testBeAbleToJoinOnCollectionOfEmbeddedWithTwoEmbeddedCollections() {
      IckleParsingResult<Class<?>> parsed = parse(
            " SELECT d.email " +
                  " FROM org.infinispan.query.dsl.embedded.impl.model.Employee e " +
                  " JOIN e.contactDetails d " +
                  " JOIN e.alternativeContactDetails a" +
                  " WHERE d.address.postCode='EA123' AND a.email='ninja647@mailinator.com'");
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString())
            .isEqualTo("+(+contactDetails.address.postCode:EA123 +alternativeContactDetails.email:ninja647@mailinator.com) #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsOnly("contactDetails.email");
   }

   @Test
   public void testSpatialPredicate() {
      IckleParsingResult<Class<?>> parsed = parse(
            "SELECT e.contactDetails.email" +
                  " FROM org.infinispan.query.dsl.embedded.impl.model.Employee e " +
                  " WHERE e.location WITHIN CIRCLE(46.7716, 23.5895, 100) AND e.location NOT WITHIN CIRCLE(46.7716, 23.5895, 10)");
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString())
            .isEqualTo("+(+location:46.7716,23.5895 +/- 100.0 meters -location:46.7716,23.5895 +/- 10.0 meters) #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsExactly("contactDetails.email");
   }

   @Test
   public void testSpatialPredicate_box() {
      IckleParsingResult<Class<?>> parsed = parse(
            "SELECT e.contactDetails.email" +
                  " FROM org.infinispan.query.dsl.embedded.impl.model.Employee e " +
                  " WHERE e.location WITHIN box(46.7716, 23.5895, 47.7716, 24.5895) AND e.location NOT WITHIN box(46.7716, 23.5895, 47.7716, 24.5895)");
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString())
            .isEqualTo("+(+location:[47.771600000560284 TO 46.7715999763459],[23.589500058442354 TO 24.58949999883771] -location:[47.771600000560284 TO 46.7715999763459],[23.589500058442354 TO 24.58949999883771]) #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsExactly("contactDetails.email");
   }

   @Test
   public void testSpatialPredicate_polygon() {
      IckleParsingResult<Class<?>> parsed = parse(
            "SELECT e.contactDetails.email" +
                  " FROM org.infinispan.query.dsl.embedded.impl.model.Employee e " +
                  " WHERE e.location WITHIN polygon((46.7716, 23.5895), (47.7716, 24.5895), (47.7716, 24.5895)) AND e.location NOT WITHIN polygon((46.7716, 23.5895), (47.7716, 24.5895), (47.7716, 24.5895))");
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString())
            .isEqualTo("+(+LatLonPointQuery: field=location:[[46.7716, 23.5895] [47.7716, 24.5895] [47.7716, 24.5895] [46.7716, 23.5895] ,] -LatLonPointQuery: field=location:[[46.7716, 23.5895] [47.7716, 24.5895] [47.7716, 24.5895] [46.7716, 23.5895] ,]) #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsExactly("contactDetails.email");
   }

   @Test
   public void testSpatialProjection() {
      IckleParsingResult<Class<?>> parsed = parse(
            "SELECT e.contactDetails.email, distance(e.location, 37.7608, 140.4748) " +
                  " FROM org.infinispan.query.dsl.embedded.impl.model.Employee e " +
                  " WHERE e.location WITHIN CIRCLE(46.7716, 23.5895, 100) AND e.location NOT WITHIN CIRCLE(46.7716, 23.5895, 10)");
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString())
            .isEqualTo("+(+location:46.7716,23.5895 +/- 100.0 meters -location:46.7716,23.5895 +/- 10.0 meters) #__HSEARCH_type:main");

      assertThat(parsed.getProjectedPaths()).hasSize(2);
      assertThat(parsed.getProjectedPaths()[0]).isInstanceOf(PropertyPath.class);
      assertThat(parsed.getProjectedPaths()[0].asStringPathWithoutAlias()).isEqualTo("contactDetails.email");
      assertThat(parsed.getProjectedPaths()[1]).isInstanceOf(FunctionPropertyPath.class);
      assertThat(parsed.getProjectedPaths()[1].asStringPathWithoutAlias()).isEqualTo("location");
      assertThat(((FunctionPropertyPath<?>) parsed.getProjectedPaths()[1]).getFunction()).isEqualTo(Function.DISTANCE);
      assertThat(((FunctionPropertyPath<?>) parsed.getProjectedPaths()[1]).getArgs()).containsExactly(37.7608d, 140.4748d);
   }

   @Test
   public void testSpatialProjection_box() {
      IckleParsingResult<Class<?>> parsed = parse(
            "SELECT e.contactDetails.email, distance(e.location, 37.7608, 140.4748) " +
                  " FROM org.infinispan.query.dsl.embedded.impl.model.Employee e " +
                  " WHERE e.location WITHIN box(46.7716, 23.5895, 47.7716, 24.5895) AND e.location NOT WITHIN box(46.7716, 23.5895, 47.7716, 24.5895)");
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString())
            .isEqualTo("+(+location:[47.771600000560284 TO 46.7715999763459],[23.589500058442354 TO 24.58949999883771] -location:[47.771600000560284 TO 46.7715999763459],[23.589500058442354 TO 24.58949999883771]) #__HSEARCH_type:main");

      assertThat(parsed.getProjectedPaths()).hasSize(2);
      assertThat(parsed.getProjectedPaths()[0]).isInstanceOf(PropertyPath.class);
      assertThat(parsed.getProjectedPaths()[0].asStringPathWithoutAlias()).isEqualTo("contactDetails.email");
      assertThat(parsed.getProjectedPaths()[1]).isInstanceOf(FunctionPropertyPath.class);
      assertThat(parsed.getProjectedPaths()[1].asStringPathWithoutAlias()).isEqualTo("location");
      assertThat(((FunctionPropertyPath<?>) parsed.getProjectedPaths()[1]).getFunction()).isEqualTo(Function.DISTANCE);
      assertThat(((FunctionPropertyPath<?>) parsed.getProjectedPaths()[1]).getArgs()).containsExactly(37.7608d, 140.4748d);
   }

   @Test
   public void testSpatialOrderBy() {
      IckleParsingResult<Class<?>> parsed = parse(
            "SELECT e.contactDetails.email " +
                  " FROM org.infinispan.query.dsl.embedded.impl.model.Employee e " +
                  " ORDER BY distance(e.location, 37.7608, 140.4748)");
      SearchQuery<?> query = transform(parsed);

      assertThat(query.queryString()).isEqualTo("+*:* #__HSEARCH_type:main");

      assertThat(parsed.getProjections()).containsExactly("contactDetails.email");

      assertThat(parsed.getSortFields()).hasSize(1);
      assertThat(parsed.getSortFields()[0].getPath()).isInstanceOf(FunctionPropertyPath.class);
      assertThat(parsed.getSortFields()[0].getPath().asStringPathWithoutAlias()).isEqualTo("location");
      assertThat(((FunctionPropertyPath<?>) parsed.getSortFields()[0].getPath()).getFunction()).isEqualTo(Function.DISTANCE);
      assertThat(((FunctionPropertyPath<?>) parsed.getSortFields()[0].getPath()).getArgs()).containsExactly(37.7608d, 140.4748d);
   }

   private void assertGeneratedLuceneQuery(String queryString, String expectedLuceneQuery) {
      assertGeneratedLuceneQuery(queryString, null, expectedLuceneQuery);
   }

   private void assertGeneratedLuceneQuery(String queryString, Map<String, Object> namedParameters, String expectedLuceneQuery) {
      SearchQuery<?> result = parseAndTransform(queryString, namedParameters);
      assertThat(result.queryString()).isEqualTo(expectedLuceneQuery);
   }

   private SearchQuery<?> parseAndTransform(String queryString) {
      return parseAndTransform(queryString, null);
   }

   private SearchQuery<?> parseAndTransform(String queryString, Map<String, Object> namedParameters) {
      IckleParsingResult<Class<?>> ickleParsingResult = parse(queryString);
      return transform(ickleParsingResult, namedParameters);
   }

   private IckleParsingResult<Class<?>> parse(String queryString) {
      return IckleParser.parse(queryString, propertyHelper);
   }

   private SearchQuery<?> transform(IckleParsingResult<Class<?>> ickleParsingResult) {
      return transform(ickleParsingResult, null);
   }

   private SearchQuery<?> transform(IckleParsingResult<Class<?>> ickleParsingResult, Map<String, Object> parameters) {
      return transform(ickleParsingResult, parameters, Employee.class);
   }

   private SearchQuery<?> transform(IckleParsingResult<Class<?>> ickleParsingResult, Map<String, Object> parameters, Class<?> targetedType) {
      SearchQueryMaker<Class<?>> searchQueryMaker = new SearchQueryMaker<>(searchMapping, propertyHelper, 100, 10_000);
      return searchQueryMaker
            .transform(ickleParsingResult, parameters, targetedType, null)
            .builder(searchMapping.getMappingSession()).build();
   }
}
