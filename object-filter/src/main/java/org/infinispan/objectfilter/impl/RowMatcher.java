package org.infinispan.objectfilter.impl;

import java.util.Collections;
import java.util.List;

import org.infinispan.objectfilter.impl.predicateindex.RowMatcherEvalContext;
import org.infinispan.objectfilter.impl.syntax.parser.RowPropertyHelper;

/**
 * A matcher for projection rows (Object[]). This matcher is not stateless so it cannot be reused.
 *
 * @author anistor@redhat.com
 * @since 8.0
 */
public final class RowMatcher extends BaseMatcher<RowPropertyHelper.RowMetadata, RowPropertyHelper.ColumnMetadata, Integer> {

   private final RowPropertyHelper.RowMetadata rowMetadata;

   public RowMatcher(RowPropertyHelper.ColumnMetadata[] columns) {
      super(new RowPropertyHelper(columns));
      rowMetadata = ((RowPropertyHelper) propertyHelper).getRowMetadata();
   }

   @Override
   protected RowMatcherEvalContext startMultiTypeContext(boolean isDeltaFilter, Object userContext, Object eventType, Object instance) {
      FilterRegistry<RowPropertyHelper.RowMetadata, RowPropertyHelper.ColumnMetadata, Integer> filterRegistry = getFilterRegistryForType(isDeltaFilter, rowMetadata);
      if (filterRegistry != null) {
         RowMatcherEvalContext context = new RowMatcherEvalContext(userContext, eventType, null, instance, rowMetadata);
         context.initMultiFilterContext(filterRegistry);
         return context;
      }
      return null;
   }

   @Override
   protected RowMatcherEvalContext startSingleTypeContext(Object userContext, Object eventType, Object key, Object instance, Object metadata,
                                                          MetadataAdapter<RowPropertyHelper.RowMetadata, RowPropertyHelper.ColumnMetadata, Integer> metadataAdapter) {
      if (Object[].class == instance.getClass()) {
         return new RowMatcherEvalContext(userContext, eventType, key, instance, rowMetadata);
      } else {
         return null;
      }
   }

   @Override
   protected MetadataAdapter<RowPropertyHelper.RowMetadata, RowPropertyHelper.ColumnMetadata, Integer> createMetadataAdapter(RowPropertyHelper.RowMetadata rowMetadata) {
      return new MetadataAdapterImpl(rowMetadata);
   }

   private static class MetadataAdapterImpl implements MetadataAdapter<RowPropertyHelper.RowMetadata, RowPropertyHelper.ColumnMetadata, Integer> {

      private final RowPropertyHelper.RowMetadata rowMetadata;

      MetadataAdapterImpl(RowPropertyHelper.RowMetadata rowMetadata) {
         this.rowMetadata = rowMetadata;
      }

      /**
       * Implementation is just for completeness. The name does not matter.
       */
      @Override
      public String getTypeName() {
         return "Row";
      }

      @Override
      public RowPropertyHelper.RowMetadata getTypeMetadata() {
         return rowMetadata;
      }

      @Override
      public List<Integer> mapPropertyNamePathToFieldIdPath(String[] path) {
         if (path.length > 1) {
            throw new IllegalStateException("Rows do not support nested attributes");
         }

         String columnName = path[0];
         for (RowPropertyHelper.ColumnMetadata c : rowMetadata.getColumns()) {
            if (c.getColumnName().equals(columnName)) {
               return Collections.singletonList(c.getColumnIndex());
            }
         }

         throw new IllegalArgumentException("Column not found : " + columnName);
      }

      @Override
      public RowPropertyHelper.ColumnMetadata makeChildAttributeMetadata(RowPropertyHelper.ColumnMetadata parentAttributeMetadata, Integer attribute) {
         if (parentAttributeMetadata != null) {
            throw new IllegalStateException("Rows do not support nested attributes");
         }
         return rowMetadata.getColumns()[attribute];
      }

      @Override
      public boolean isComparableProperty(RowPropertyHelper.ColumnMetadata attributeMetadata) {
         Class<?> propertyType = attributeMetadata.getPropertyType();
         return propertyType.isPrimitive() || Comparable.class.isAssignableFrom(propertyType);
      }
   }
}
