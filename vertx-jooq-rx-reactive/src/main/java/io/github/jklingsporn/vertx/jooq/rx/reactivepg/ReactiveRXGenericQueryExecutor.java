package io.github.jklingsporn.vertx.jooq.rx.reactivepg;

import io.github.jklingsporn.vertx.jooq.rx.RXQueryExecutor;
import io.github.jklingsporn.vertx.jooq.shared.internal.QueryResult;
import io.github.jklingsporn.vertx.jooq.shared.reactive.AbstractReactiveQueryExecutor;
import io.github.jklingsporn.vertx.jooq.shared.reactive.ReactiveQueryExecutor;
import io.github.jklingsporn.vertx.jooq.shared.reactive.ReactiveQueryResult;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.reactivex.impl.AsyncResultCompletable;
import io.vertx.reactivex.impl.AsyncResultSingle;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.*;
import io.vertx.sqlclient.Transaction;
import org.jooq.*;
import org.jooq.conf.ParamType;
import org.jooq.exception.TooManyRowsException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Created by jensklingsporn on 01.03.18.
 */
public class ReactiveRXGenericQueryExecutor extends AbstractReactiveQueryExecutor implements
        ReactiveQueryExecutor<Single<List<Row>>, Single<Optional<Row>>, Single<Integer>>, RXQueryExecutor {

    protected final Pool delegate;

    public ReactiveRXGenericQueryExecutor(Configuration configuration, Pool delegate) {
        super(configuration);
        this.delegate = delegate;
    }

    @Override
    public <Q extends Record> Single<List<Row>> findManyRow(Function<DSLContext, ? extends
            ResultQuery<Q>> queryFunction) {
        Query query = createQuery(queryFunction);
        log(query);
        Single<RowSet> rowSingle = delegate.rxPreparedQuery(toPreparedQuery(query), rxGetBindValues(query));
        return rowSingle.map(res ->
                StreamSupport
                        .stream(res.spliterator(), false)
                        .collect(Collectors.toList()));
    }

    protected Single<SqlConnection> getConnection() {
        return delegate.rxGetConnection();
    }

    protected <U> io.reactivex.functions.Function<io.vertx.reactivex.ext.sql.SQLConnection,
            Single<? extends U>> safeExecute(io.reactivex.functions.Function<io.vertx.reactivex.ext.sql.SQLConnection, Single<? extends U>> action) {
        return sqlConnection -> {
            try {
                return action.apply(sqlConnection);
            } catch (Throwable e) {
                sqlConnection.close();
                return Single.error(e);
            }
        };
    }

    @Override
    public <Q extends Record> Single<Optional<Row>> findOneRow(Function<DSLContext, ? extends ResultQuery<Q>> queryFunction) {
        Query query = createQuery(queryFunction);
        log(query);
        Single<RowSet> rowSingle = delegate.rxPreparedQuery(toPreparedQuery(query), rxGetBindValues(query));
        return rowSingle.map(res -> {
            switch (res.size()) {
                case 0:
                    return Optional.empty();
                case 1:
                    return Optional.ofNullable(res.iterator().next());
                default:
                    throw new TooManyRowsException(String.format("Found more than one row: %d", res.size()));
            }
        });
    }

    @Override
    public Single<Integer> execute(Function<DSLContext, ? extends Query> queryFunction) {
        Query query = createQuery(queryFunction);
        log(query);
        Single<RowSet> rowSingle = AsyncResultSingle.toSingle(handler -> {
            delegate.preparedQuery(toPreparedQuery(query), rxGetBindValues(query), handler);
        });
        return rowSingle.map(RowSet::rowCount);
    }

    protected Tuple rxGetBindValues(Query query) {
        ArrayList<Object> bindValues = new ArrayList<>();
        for (Param<?> param : query.getParams().values()) {
            if (ParamType.INDEXED.equals(param.getParamType())) {
                Object value = convertToDatabaseType(param);
                bindValues.add(value);
            }
        }
        Tuple tuple = Tuple.tuple();
        bindValues.forEach(tuple::addValue);
        return tuple;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R extends Record> Single<QueryResult> query(Function<DSLContext, ? extends ResultQuery<R>> queryFunction) {
        Query query = createQuery(queryFunction);
        log(query);
        Single<RowSet> rowSingle = AsyncResultSingle.toSingle(handler -> {
            delegate.preparedQuery(toPreparedQuery(query), rxGetBindValues(query), handler);
        });
        return rowSingle.map(res -> new ReactiveQueryResult(res.getDelegate()));
    }

    /**
     * @return an instance of a <code>ReactiveRXGenericQueryExecutor</code> that performs all CRUD
     * functions in the scope of a transaction. The transaction has to be committed/rolled back by calling <code>commit</code>
     * or <code>rollback</code> on the QueryExecutor returned.
     */
//    public Single<? extends ReactiveRXGenericQueryExecutor> beginTransaction() {
//        if (delegate instanceof Transaction) {
//            throw new IllegalStateException("Already in transaction");
//        }
//        return AsyncResultSingle.toSingle(handler -> {
//           delegate.getConnection();
//        });
//    }

    /**
     * Commits a transaction.
     *
     * @return a <code>Completable</code> that completes when the transaction has been committed.
     * @throws IllegalStateException if not called <code>beginTransaction</code> before.
     */
    public Completable commit() {
        if (!(delegate instanceof Transaction)) {
            throw new IllegalStateException("Not in transaction");
        }
        return AsyncResultCompletable.toCompletable(handler -> {
            ((Transaction) delegate).commit(handler);
        });
    }

    /**
     * Rolls a transaction back.
     *
     * @return a <code>Completable</code> that completes when the transaction has been rolled back.
     * @throws IllegalStateException if not called <code>beginTransaction</code> before.
     */
    public Completable rollback() {
        if (!(delegate instanceof io.vertx.sqlclient.Transaction)) {
            throw new IllegalStateException("Not in transaction");
        }
        return AsyncResultCompletable.toCompletable(handler -> {
            ((Transaction) delegate).rollback(handler);
        });
    }

    /**
     * Convenience method to perform multiple calls on a transactional QueryExecutor, committing the transaction and
     * returning a result.
     *
     * @param transaction your code using a transactional QueryExecutor.
     *                    <pre>
     *                                       {@code
     *                                       ReactiveRXGenericQueryExecutor nonTransactionalQueryExecutor...;
     *                                       Maybe<QueryResult> resultOfTransaction = nonTransactionalQueryExecutor.transaction(transactionalQueryExecutor ->
     *                                         {
     *                                             //make all calls on the provided QueryExecutor that runs all code in a transaction
     *                                             return transactionalQueryExecutor.execute(dslContext -> dslContext.insertInto(Tables.XYZ)...)
     *                                                 .compose(i -> transactionalQueryExecutor.query(dslContext -> dslContext.selectFrom(Tables.XYZ).where(Tables.XYZ.SOME_VALUE.eq("FOO")));
     *                                         }
     *                                       );
     *                                       }
     *                                       </pre>
     * @param <U>         the return type.
     * @return the result of the transaction.
     */
//    public <U> Maybe<U> transaction(io.reactivex.functions.Function<ReactiveRXGenericQueryExecutor, Maybe<U>> transaction) {
//        return beginTransaction()
//                .toMaybe()
//                .flatMap(queryExecutor -> transaction.apply(queryExecutor) //perform user tasks
//                        .flatMap(res -> queryExecutor.commit() //commit the transaction
//                                .andThen(Maybe.just(res)))) //and return the result
//                ;
//    }
}
