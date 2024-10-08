package org.infinispan.objectfilter;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;

/**
 * A filter that tests if an object matches a pre-defined condition and returns either the original instance or the
 * projection, depending on how the filter was created. The projection is represented as an Object[]. If the given
 * instance does not match the filter will just return null.
 *
 * @author anistor@redhat.com
 * @since 7.0
 */
public interface ObjectFilter {

   /**
    * The fully qualified entity type name accepted by this filter.
    */
   String getEntityTypeName();

   /**
    * The array of '.' separated path names of the projected fields if any, or {@code null} otherwise.
    */
   String[] getProjection();

   /**
    * The types of the projects paths (see {@link #getProjection()} or {@code null} if there are no projections.
    */
   Class<?>[] getProjectionTypes();

   /**
    * Returns the filter parameter names or an empty {@link Set} if there are no parameters.
    */
   Set<String> getParameterNames();

   /**
    * The parameter values. Please do not mutate this map. Obtain a new filter instance with new parameter values using
    * {@link #withParameters(Map)} if you have to.
    */
   Map<String, Object> getParameters();

   /**
    * Creates a new ObjectFilter instance based on the current one and the given parameter values.
    */
   ObjectFilter withParameters(Map<String, Object> namedParameters);

   /**
    * The array of sort specifications if defined, or {@code null} otherwise.
    */
   SortField[] getSortFields();

   /**
    * The comparator corresponding to the 'order by' clause, if any.
    *
    * @return the Comparator or {@code null} if no 'order by' was specified ({@link #getSortFields()} also returns
    * {@code null})
    */
   Comparator<Comparable<?>[]> getComparator();

   /**
    * Tests if an object matches the filter. A shorthand for {@code filter(null, value, null)}.
    *
    * @param value the instance to test; this is never {@code null}
    * @return a {@link FilterResult} if there is a match or {@code null} otherwise
    */
   default FilterResult filter(Object value) {
      return filter(null, value, null);
   }

   /**
    * Tests if an object matches the filter. The given key is optional (can be null) and will be returned to
    * the user in the {@link FilterResult}.
    *
    * @param key   the (optional) key; this can be {@code null} if it is of no interest
    * @param value the instance to test; this is never {@code null}
    * @param metadata the (optional) metadata; this can be {@code null} if it is of no interest
    * @return a {@link FilterResult} if there is a match or {@code null} otherwise
    */
   FilterResult filter(Object key, Object value, Object metadata);

   /**
    * The output of the {@link ObjectFilter#filter} method.
    */
   interface FilterResult {

      /**
       * Returns the key of the matched object. This is optional.
       */
      Object getKey();

      /**
       * Returns the matched object. This is non-null unless projections are present.
       */
      Object getInstance();

      /**
       * Returns the projection, if a projection was requested or {@code null} otherwise.
       */
      Object[] getProjection();

      /**
       * Returns the projection of fields used for sorting, if sorting was requested or {@code null} otherwise.
       */
      Comparable<?>[] getSortProjection();
   }
}
