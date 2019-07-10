package io.github.jklingsporn.vertx.jooq.generate.builder;

import io.github.jklingsporn.vertx.jooq.shared.JsonArrayConverter;
import io.github.jklingsporn.vertx.jooq.shared.JsonObjectConverter;
import io.github.jklingsporn.vertx.jooq.shared.ObjectToJsonObjectBinding;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.jooq.Configuration;
import org.jooq.codegen.GeneratorStrategy;
import org.jooq.codegen.JavaWriter;
import org.jooq.meta.ColumnDefinition;
import org.jooq.meta.TableDefinition;
import org.jooq.meta.UniqueKeyDefinition;

import java.io.File;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.function.Function;

/**
 * Builder to create a {@code VertxGenerator}. Non-instantiable, see static init() method.
 *
 * @author jensklingsporn
 */
public class VertxGeneratorBuilder {

    static final Map<String, String> SUPPORTED_MYSQL_INSERT_RETURNING_TYPES_MAP;

    static {
        SUPPORTED_MYSQL_INSERT_RETURNING_TYPES_MAP = new HashMap<>();
        SUPPORTED_MYSQL_INSERT_RETURNING_TYPES_MAP.put(Byte.class.getSimpleName(), byte.class.getSimpleName());
        SUPPORTED_MYSQL_INSERT_RETURNING_TYPES_MAP.put(Short.class.getSimpleName(), short.class.getSimpleName());
        SUPPORTED_MYSQL_INSERT_RETURNING_TYPES_MAP.put(Integer.class.getSimpleName(), int.class.getSimpleName());
        SUPPORTED_MYSQL_INSERT_RETURNING_TYPES_MAP.put(Long.class.getSimpleName(), long.class.getSimpleName());
    }

    enum APIType {
        CLASSIC,
        COMPLETABLE_FUTURE,
        RX;
    }


    private VertxGeneratorBuilder() {
    }

    /**
     * @return an {@code APIStep} to init the build of a {@code VertxGeneratorStrategy}.
     */
    public static APIStep init() {
        return new APIStepImpl(new ComponentBasedVertxGenerator()
                .setRenderFQVertxNameDelegate(() -> "io.vertx.core.Vertx"));
    }

    static class APIStepImpl implements APIStep {

        private final ComponentBasedVertxGenerator base;

        APIStepImpl(ComponentBasedVertxGenerator base) {
            this.base = base;
            this.base.addOverwriteDAODelegate((out, className, tableIdentifier, tableRecord, pType, tType) -> {
                out.println();
                out.tab(1).override();
                out.tab(1).println("public %s queryExecutor(){", base.renderQueryExecutor(tableRecord, pType, tType));
                out.tab(2).println("return (%s) super.queryExecutor();", base.renderQueryExecutor(tableRecord, pType, tType));
                out.tab(1).println("}");
            });
        }

        @Override
        public ExecutionStep withRXAPI() {
            return new ExecutionStepImpl(base
                    .setRenderFQVertxNameDelegate(() -> "io.vertx.reactivex.core.Vertx")
                    .setApiType(APIType.RX)
                    .setWriteDAOImportsDelegate(out -> {
                        out.println("import io.reactivex.Single;");
                        out.println("import java.util.Optional;");
                    })
                    .setRenderQueryExecutorTypesDelegate(new RenderQueryExecutorTypesComponent() {
                        @Override
                        public String renderFindOneType(String pType) {
                            return String.format("Single<Optional<%s>>", pType);
                        }

                        @Override
                        public String renderFindManyType(String pType) {
                            return String.format("Single<List<%s>>", pType);
                        }

                        @Override
                        public String renderExecType() {
                            return "Single<Integer>";
                        }

                        @Override
                        public String renderInsertReturningType(String tType) {
                            return String.format("Single<%s>", tType);
                        }
                    })
                    .setRenderDAOInterfaceDelegate((rType, pType, tType) -> String.format("io.github.jklingsporn.vertx.jooq.rx.VertxDAO<%s,%s,%s>", rType, pType, tType))
            );
        }
    }

    static class ExecutionStepImpl implements ExecutionStep {

        private final ComponentBasedVertxGenerator base;

        ExecutionStepImpl(ComponentBasedVertxGenerator base) {
            this.base = base;
        }

        @Override
        public DIStep withReactiveDriver() {
            base.setRenderDAOExtendsDelegate(() -> "io.github.jklingsporn.vertx.jooq.shared.reactive.AbstractReactiveVertxDAO");
            base.addWriteExtraDataDelegate((schema, writerGen) -> {
                ComponentBasedVertxGenerator.logger.info("Generate RowMappers ... ");
                String mappersSubPackage = base.getActiveGenerator().getVertxGeneratorStrategy().getRowMappersSubPackage();
                String packageName = base.getActiveGenerator().getStrategy().getJavaPackageName(schema) + ".tables." + mappersSubPackage;
                String dir = base.getActiveGenerator().getStrategy().getTargetDirectory();
                dir = File.separator.equals("/") ? dir.replace("\\", File.separator) : dir.replace("/", File.separator);
                dir = dir + File.separator + packageName.replace(".", File.separator);
                File moduleFile = new File(dir, "RowMappers.java");
                JavaWriter out = writerGen.apply(moduleFile);
                out.println("package " + base.getActiveGenerator().getStrategy().getJavaPackageName(schema) + ".tables." + mappersSubPackage + ";");
                out.println();
                out.println("import io.vertx.reactivex.sqlclient.Row;");
                out.println("import %s;", Function.class.getName());
                out.println();
                out.println("public class RowMappers {");
                out.println();
                out.tab(1).println("private RowMappers(){}"); //not instantiable
                out.println();
                Set<String> supportedRowTypes = new HashSet<>();
                supportedRowTypes.add(Boolean.class.getName());
                supportedRowTypes.add(Short.class.getName());
                supportedRowTypes.add(Integer.class.getName());
                supportedRowTypes.add(Long.class.getName());
                supportedRowTypes.add(Float.class.getName());
                supportedRowTypes.add(Double.class.getName());
                supportedRowTypes.add(BigDecimal.class.getName());
                supportedRowTypes.add(String.class.getName());
                supportedRowTypes.add(Character.class.getName());
                supportedRowTypes.add(Buffer.class.getName());
                supportedRowTypes.add(UUID.class.getName());
                supportedRowTypes.add(Instant.class.getName());
                supportedRowTypes.add(Temporal.class.getName());
                supportedRowTypes.add(LocalTime.class.getName());
                supportedRowTypes.add(LocalDate.class.getName());
                supportedRowTypes.add(LocalDateTime.class.getName());
                supportedRowTypes.add(OffsetTime.class.getName());
                supportedRowTypes.add(OffsetDateTime.class.getName());
                for (TableDefinition table : schema.getTables()) {
                    UniqueKeyDefinition key = table.getPrimaryKey();
                    if (key == null) {
                        ComponentBasedVertxGenerator.logger.info("{} has no primary key. Skipping...", out.file().getName());
                        continue;
                    }
                    final String pType = base.getActiveGenerator().getStrategy().getFullJavaClassName(table, GeneratorStrategy.Mode.POJO);
                    out.tab(1).println("public static Function<Row,%s> get%sMapper() {", pType, base.getActiveGenerator().getStrategy().getJavaClassName(table, GeneratorStrategy.Mode.POJO));
                    out.tab(2).println("return row -> {");
                    out.tab(3).println("%s pojo = new %s();", pType, pType);
                    for (ColumnDefinition column : table.getColumns()) {
                        String setter = base.getActiveGenerator().getStrategy().getJavaSetterName(column, GeneratorStrategy.Mode.INTERFACE);
                        String javaType = base.getActiveGenerator().getJavaType(column.getType());
                        //is there a better way to check for enum type rather than checking the package?
                        boolean javaTypeInEnumPackage = javaType.contains("enums.");
                        if (supportedRowTypes.contains(javaType)) {
                            try {
                                out.tab(3).println("pojo.%s(row.get%s(\"%s\"));", setter, Class.forName(javaType).getSimpleName(), column.getName());
                            } catch (ClassNotFoundException e) {
                                ComponentBasedVertxGenerator.logger.error(e.getMessage(), e);
                            }
                        } else if (javaType.equals(JsonObject.class.getName())
                                || (column.getType().getConverter() != null && column.getType().getConverter().equalsIgnoreCase(JsonObjectConverter.class.getName()))
                                || (column.getType().getBinding() != null && column.getType().getBinding().equalsIgnoreCase(ObjectToJsonObjectBinding.class.getName()))) {
//                            out.tab(3).println("pojo.%s(io.github.jklingsporn.vertx.jooq.shared.reactive.JsonAccessor.getJsonObject(row,\"%s\"));", setter, column.getName());
                        } else if (javaType.equals(JsonArray.class.getName())
                                || (column.getType().getConverter() != null && column.getType().getConverter().equalsIgnoreCase(JsonArrayConverter.class.getName()))) {
//                            out.tab(3).println("pojo.%s(io.github.jklingsporn.vertx.jooq.shared.reactive.JsonAccessor.getJsonArray(row,\"%s\"));", setter, column.getName());
                        } else if (javaTypeInEnumPackage) {
                            out.tab(3).println("pojo.%s(%s.valueOf(row.getString(\"%s\")));", setter, javaType, column.getName());
                        } else {
                            ComponentBasedVertxGenerator.logger.warn(String.format("Omitting unrecognized type %s (%s) for column %s in table %s!", column.getType(), javaType, column.getName(), table.getName()));
                            out.tab(3).println(String.format("// Omitting unrecognized type %s (%s) for column %s!", column.getType(), javaType, column.getName()));
                        }
                    }
                    out.tab(3).println("return pojo;");
                    out.tab(2).println("};");
                    out.tab(1).println("}");
                    out.println();
                }
                out.println("}");
                return out;
            });
            return new DIStepImpl(base
                    .setWriteDAOImportsDelegate(base.writeDAOImportsDelegate.andThen(out -> out.println("import io.github.jklingsporn.vertx.jooq.rx.reactivepg.ReactiveRXQueryExecutor;")))
                    .setRenderQueryExecutorDelegate((rType, pType, tType) -> String.format("ReactiveRXQueryExecutor<%s,%s,%s>", rType, pType, tType))
                    .setWriteConstructorDelegate((out, className, tableIdentifier, tableRecord, pType, tType, schema) -> {
                        /*
                         * pType = foo.bar.pojos.Somepojo
                         * -------^-need--^---------------
                         * temp = foo.bar.pojos
                         */
                        String temp = pType.substring(0, pType.lastIndexOf('.'));
                        String basePath = temp.substring(0, temp.lastIndexOf('.'));
                        String pojoName = pType.substring(pType.lastIndexOf(".") + 1, pType.length());
                        String mapperFactory = String.format("%s.%s.RowMappers.get%sMapper()", basePath, base.getVertxGeneratorStrategy().getRowMappersSubPackage(), pojoName);
                        out.tab(1).javadoc("@param configuration The Configuration used for rendering and query execution.\n" +
                                "     * @param vertx the vertx instance");
                        out.tab(1).println("public %s(%s configuration, %sio.vertx.reactivex.sqlclient.Pool delegate) {", className, Configuration.class, base.namedInjectionStrategy.apply(schema));
                        out.tab(2).println("super(%s, %s.class, new %s(configuration,delegate,%s));", tableIdentifier, pType, base.renderQueryExecutor(tableRecord, pType, tType), mapperFactory);
                        out.tab(1).println("}");
                    }));
        }
    }

    static class DIStepImpl extends FinalStepImpl implements DIStep {

        public DIStepImpl(ComponentBasedVertxGenerator base) {
            super(base);
        }

        @Override
        public FinalStep withGuice(boolean generateGuiceModules, NamedInjectionStrategy namedInjectionStrategy) {
            base.setWriteDAOConstructorAnnotationDelegate((out) -> out.tab(1).println("@javax.inject.Inject"));
            base.setWriteDAOClassAnnotationDelegate((out) -> out.println("@javax.inject.Singleton"));
            base.setNamedInjectionStrategy(namedInjectionStrategy);
            if (generateGuiceModules) {
                base.addWriteExtraDataDelegate((schema, writerGen) -> {
                    ComponentBasedVertxGenerator.logger.info("Generate DaoModule ... ");
                    String daoClassName;
                    switch (base.apiType) {
                        case CLASSIC:
                            daoClassName = "io.github.jklingsporn.vertx.jooq.classic.VertxDAO";
                            break;
                        case COMPLETABLE_FUTURE:
                            daoClassName = "io.github.jklingsporn.vertx.jooq.completablefuture.VertxDAO";
                            break;
                        case RX:
                            daoClassName = "io.github.jklingsporn.vertx.jooq.rx.VertxDAO";
                            break;
                        default:
                            throw new UnsupportedOperationException(base.apiType.toString());
                    }
                    String packageName = (base.getActiveGenerator().getStrategy().getTargetDirectory() + "/" + base.getActiveGenerator().getStrategy().getJavaPackageName(schema) + ".tables.modules").replaceAll("\\.", "/");
                    File moduleFile = new File(packageName, "DaoModule.java");
                    JavaWriter out = writerGen.apply(moduleFile);
                    out.println("package " + base.getActiveGenerator().getStrategy().getJavaPackageName(schema) + ".tables.modules;");
                    out.println();
                    out.println("import com.google.inject.AbstractModule;");
                    out.println("import com.google.inject.TypeLiteral;");
                    out.println("import %s;", daoClassName);
                    out.println();
                    out.println("public class DaoModule extends AbstractModule {");
                    out.tab(1).println("@Override");
                    out.tab(1).println("protected void configure() {");
                    for (TableDefinition table : schema.getTables()) {
                        UniqueKeyDefinition key = table.getPrimaryKey();
                        if (key == null) {
                            ComponentBasedVertxGenerator.logger.info("{} has no primary key. Skipping...", out.file().getName());
                            continue;
                        }
                        final String keyType = base.getActiveGenerator().getKeyType(key);
                        final String tableRecord = base.getActiveGenerator().getStrategy().getFullJavaClassName(table, GeneratorStrategy.Mode.RECORD);
                        final String pType = base.getActiveGenerator().getStrategy().getFullJavaClassName(table, GeneratorStrategy.Mode.POJO);
                        final String className = base.getActiveGenerator().getStrategy().getFullJavaClassName(table, GeneratorStrategy.Mode.DAO);
                        if (base.generateInterfaces()) {
                            String iType = base.getActiveGenerator().getStrategy().getFullJavaClassName(table, GeneratorStrategy.Mode.INTERFACE);
                            out.tab(2).println("bind(new TypeLiteral<VertxDAO<%s, ? extends %s, %s>>() {}).to(%s.class).asEagerSingleton();",
                                    tableRecord, iType, keyType, className);
                        }
                        out.tab(2).println("bind(new TypeLiteral<VertxDAO<%s, %s, %s>>() {}).to(%s.class).asEagerSingleton();",
                                tableRecord, pType, keyType, className);
                    }
                    out.tab(1).println("}");
                    out.println("}");
                    return out;
                });
            }
            return new FinalStepImpl(base);
        }


    }

    static class FinalStepImpl implements FinalStep {

        protected final ComponentBasedVertxGenerator base;

        FinalStepImpl(ComponentBasedVertxGenerator base) {
            this.base = base;
        }

        @Override
        public ComponentBasedVertxGenerator build() {
            return base;
        }


    }

}
