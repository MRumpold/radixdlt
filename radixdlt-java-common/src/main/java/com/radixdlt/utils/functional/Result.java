/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.utils.functional;

import com.radixdlt.utils.functional.Functions.FN1;
import com.radixdlt.utils.functional.Functions.FN2;
import com.radixdlt.utils.functional.Functions.FN3;
import com.radixdlt.utils.functional.Functions.FN4;
import com.radixdlt.utils.functional.Functions.FN5;
import com.radixdlt.utils.functional.Functions.FN6;
import com.radixdlt.utils.functional.Functions.FN7;
import com.radixdlt.utils.functional.Functions.FN8;
import com.radixdlt.utils.functional.Functions.FN9;
import com.radixdlt.utils.functional.Tuple.Tuple1;
import com.radixdlt.utils.functional.Tuple.Tuple2;
import com.radixdlt.utils.functional.Tuple.Tuple3;
import com.radixdlt.utils.functional.Tuple.Tuple4;
import com.radixdlt.utils.functional.Tuple.Tuple5;
import com.radixdlt.utils.functional.Tuple.Tuple6;
import com.radixdlt.utils.functional.Tuple.Tuple7;
import com.radixdlt.utils.functional.Tuple.Tuple8;
import com.radixdlt.utils.functional.Tuple.Tuple9;

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.radixdlt.utils.functional.Tuple.tuple;

/**
 * Representation of the operation result. The result can be either success or failure.
 * In case of success it holds value returned by the operation. In case of failure it
 * holds a failure description.
 *
 * @param <T> Type of value in case of success.
 */
public interface Result<T> {
	/**
	 * Handle success and failure cases and produce some resulting value.
	 *
	 * @param leftMapper Function to transform the error value.
	 * @param rightMapper Function to transform the success value.
	 *
	 * @return transformed value.
	 */
	<R> R fold(Function<? super Failure, ? extends R> leftMapper, Function<? super T, ? extends R> rightMapper);

	/**
	 * Transform operation result value into value of other type and wrap new
	 * value into {@link Result}. Transformation takes place if current instance
	 * (this) contains successful result, otherwise current instance remains
	 * unchanged and transformation function is not invoked.
	 *
	 * @param mapper Function to transform successful value
	 *
	 * @return transformed value (in case of success) or current instance (in case of failure)
	 */
	@SuppressWarnings("unchecked")
	default <R> Result<R> map(Function<? super T, R> mapper) {
		return fold(l -> (Result<R>) this, r -> ok(mapper.apply(r)));
	}

	/**
	 * Transform operation result into another operation result. In case if current
	 * instance (this) is an error, transformation function is not invoked
	 * and value remains the same.
	 *
	 * @param mapper Function to apply to result
	 *
	 * @return transformed value (in case of success) or current instance (in case of failure)
	 */
	@SuppressWarnings("unchecked")
	default <R> Result<R> flatMap(Function<? super T, Result<R>> mapper) {
		return fold(t -> (Result<R>) this, mapper);
	}

	/**
	 * Apply consumers to result value. Note that depending on the result (success or failure) only one consumer will be
	 * applied at a time.
	 *
	 * @param failureConsumer Consumer for failure result
	 * @param successConsumer Consumer for success result
	 *
	 * @return current instance
	 */
	default Result<T> apply(Consumer<? super Failure> failureConsumer, Consumer<? super T> successConsumer) {
		return fold(t -> {
			failureConsumer.accept(t);
			return this;
		}, t -> {
			successConsumer.accept(t);
			return this;
		});
	}

	/**
	 * Combine current instance with another result. If current instance holds
	 * success then result is equivalent to current instance, otherwise other
	 * instance (passed as {@code replacement} parameter) is returned.
	 *
	 * @param replacement Value to return if current instance contains failure operation result
	 *
	 * @return current instance in case of success or replacement instance in case of failure.
	 */
	default Result<T> or(Result<T> replacement) {
		return fold(t -> replacement, t -> this);
	}

	/**
	 * Combine current instance with another result. If current instance holds
	 * success then result is equivalent to current instance, otherwise instance provided by
	 * specified supplier is returned.
	 *
	 * @param supplier Supplier for replacement instance if current instance contains failure operation result
	 *
	 * @return current instance in case of success or result returned by supplier in case of failure.
	 */
	default Result<T> or(Supplier<Result<T>> supplier) {
		return fold(t -> supplier.get(), t -> this);
	}

	/**
	 * Pass successful operation result value into provided consumer.
	 *
	 * @param consumer Consumer to pass value to
	 *
	 * @return current instance for fluent call chaining
	 */
	Result<T> onSuccess(Consumer<T> consumer);

	/**
	 * Run provided action in case of success.
	 *
	 * @return current instance for fluent call chaining
	 */
	Result<T> onSuccessDo(Runnable action);

	/**
	 * Run provided action in case of failure.
	 *
	 * @return current instance for fluent call chaining
	 */
	Result<T> onFailureDo(Runnable action);

	/**
	 * Pass failure operation result value into provided consumer.
	 *
	 * @param consumer Consumer to pass value to
	 *
	 * @return current instance for fluent call chaining
	 */
	Result<T> onFailure(Consumer<? super Failure> consumer);

	/**
	 * Check for success.
	 *
	 * @return {@code true} if result is a success
	 */
	default boolean isSuccess() {
		return fold(__ -> false, __ -> true);
	}

	/**
	 * Filter contained value with given predicate. Provided failure is used for the result
	 * if predicate returns {@code false}.
	 *
	 * @param predicate Predicate to check
	 * @param failure Failure which will be used in case if predicate returns {@code false}
	 *
	 * @return the same instance if predicate returns {@code true} or new failure result with provided failure.
	 */
	default Result<T> filter(Predicate<T> predicate, Failure failure) {
		return flatMap(v -> predicate.test(v) ? this : failure.with(v).result());
	}

	/**
	 * Convert instance into {@link Optional} of the same type. Successful instance
	 * is converted into present {@link Optional} and failure - into empty {@link Optional}.
	 * Note that during such a conversion error information may get lost.
	 *
	 * @return {@link Optional} instance which is present in case of success and missing
	 * 	in case of failure.
	 */
	default Optional<T> toOptional() {
		return fold(t1 -> Optional.empty(), Optional::of);
	}

	/**
	 * Convert instance into {@link Result}
	 *
	 * @param failure failure to use when input is empty instance.
	 *
	 * @param source input instance of {@link Optional}
	 * @return created instance
	 */
	static <T> Result<T> fromOptional(Failure failure, Optional<T> source) {
		return source.map(Result::ok).orElseGet(failure::result);
	}

	/**
	 * Wrap call to function which may throw an exception.
	 *
	 * @param supplier the function to call.
	 *
	 * @return success instance if call was successful and failure instance if function threw an exception.
	 */
	static <T> Result<T> wrap(Failure failure, ThrowingSupplier<T> supplier) {
		try {
			return ok(supplier.get());
		} catch (Throwable e) {
			return failure.with(e).result();
		}
	}

	/**
	 * Create an instance of successful operation result.
	 *
	 * @param value Operation result
	 *
	 * @return created instance
	 */
	static <R> Result<R> ok(R value) {
		return new ResultOk<>(value);
	}

	/**
	 * Create an instance of failure operation result.
	 *
	 * @param value Operation error value
	 *
	 * @return created instance
	 */
	static <R> Result<R> fail(Failure value) {
		return new ResultFail<>(value);
	}

	final class ResultOk<R> implements Result<R> {
		private final R value;

		protected ResultOk(R value) {
			this.value = value;
		}

		@Override
		public <T> T fold(
			Function<? super Failure, ? extends T> leftMapper,
			Function<? super R, ? extends T> rightMapper
		) {
			return rightMapper.apply(value);
		}

		@Override
		public int hashCode() {
			return Objects.hash(value);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}

			return (obj instanceof Result<?>)
				   ? ((Result<?>) obj).fold($ -> false, val -> Objects.equals(val, value))
				   : false;
		}

		@Override
		public String toString() {
			return new StringJoiner(", ", "Result-success(", ")")
				.add(value.toString())
				.toString();
		}

		@Override
		public Result<R> onSuccess(Consumer<R> consumer) {
			consumer.accept(value);
			return this;
		}

		@Override
		public Result<R> onSuccessDo(Runnable action) {
			action.run();
			return this;
		}

		@Override
		public Result<R> onFailure(Consumer<? super Failure> consumer) {
			return this;
		}

		@Override
		public Result<R> onFailureDo(Runnable action) {
			return this;
		}
	}

	final class ResultFail<R> implements Result<R> {
		private final Failure value;

		protected ResultFail(Failure value) {
			this.value = value;
		}

		@Override
		public <T> T fold(
			Function<? super Failure, ? extends T> leftMapper,
			Function<? super R, ? extends T> rightMapper
		) {
			return leftMapper.apply(value);
		}

		@Override
		public int hashCode() {
			return Objects.hash(value);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}

			return (obj instanceof Result<?>)
				   ? ((Result<?>) obj).fold(val -> Objects.equals(val, value), $ -> false)
				   : false;
		}

		@Override
		public String toString() {
			return new StringJoiner(", ", "Result-failure(", ")")
				.add(value.toString())
				.toString();
		}

		@Override
		public Result<R> onSuccess(Consumer<R> consumer) {
			return this;
		}

		@Override
		public Result<R> onSuccessDo(Runnable action) {
			return this;
		}

		@Override
		public Result<R> onFailure(Consumer<? super Failure> consumer) {
			consumer.accept(value);
			return this;
		}

		@Override
		public Result<R> onFailureDo(Runnable action) {
			action.run();
			return this;
		}
	}

	static <T1> Mapper1<T1> allOf(Result<T1> op1) {
		return () -> op1.flatMap(v1 -> ok(tuple(v1)));
	}

	static <T1, T2> Mapper2<T1, T2> allOf(Result<T1> op1, Result<T2> op2) {
		return () -> op1.flatMap(v1 -> op2.flatMap(v2 -> ok(tuple(v1, v2))));
	}

	static <T1, T2, T3> Mapper3<T1, T2, T3> allOf(Result<T1> op1, Result<T2> op2, Result<T3> op3) {
		return () -> op1.flatMap(v1 -> op2.flatMap(v2 -> op3.flatMap(v3 -> ok(tuple(v1, v2, v3)))));
	}

	static <T1, T2, T3, T4> Mapper4<T1, T2, T3, T4> allOf(
		Result<T1> op1, Result<T2> op2, Result<T3> op3, Result<T4> op4
	) {
		return () -> op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> ok(tuple(v1, v2, v3, v4))))));
	}

	static <T1, T2, T3, T4, T5> Mapper5<T1, T2, T3, T4, T5> allOf(
		Result<T1> op1, Result<T2> op2, Result<T3> op3, Result<T4> op4, Result<T5> op5
	) {
		return () -> op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> ok(tuple(v1, v2, v3, v4, v5)))))));
	}

	static <T1, T2, T3, T4, T5, T6> Mapper6<T1, T2, T3, T4, T5, T6> allOf(
		Result<T1> op1, Result<T2> op2, Result<T3> op3,
		Result<T4> op4, Result<T5> op5, Result<T6> op6
	) {
		return () -> op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> op6.flatMap(
								v6 -> ok(tuple(v1, v2, v3, v4, v5, v6))))))));
	}

	static <T1, T2, T3, T4, T5, T6, T7> Mapper7<T1, T2, T3, T4, T5, T6, T7> allOf(
		Result<T1> op1, Result<T2> op2, Result<T3> op3, Result<T4> op4,
		Result<T5> op5, Result<T6> op6, Result<T7> op7
	) {
		return () -> op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> op6.flatMap(
								v6 -> op7.flatMap(
									v7 -> ok(tuple(v1, v2, v3, v4, v5, v6, v7)))))))));
	}

	static <T1, T2, T3, T4, T5, T6, T7, T8> Mapper8<T1, T2, T3, T4, T5, T6, T7, T8> allOf(
		Result<T1> op1, Result<T2> op2, Result<T3> op3, Result<T4> op4,
		Result<T5> op5, Result<T6> op6, Result<T7> op7, Result<T8> op8
	) {
		return () -> op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> op6.flatMap(
								v6 -> op7.flatMap(
									v7 -> op8.flatMap(
										v8 -> ok(tuple(v1, v2, v3, v4, v5, v6, v7, v8))))))))));
	}

	static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Mapper9<T1, T2, T3, T4, T5, T6, T7, T8, T9> allOf(
		Result<T1> op1, Result<T2> op2, Result<T3> op3, Result<T4> op4, Result<T5> op5,
		Result<T6> op6, Result<T7> op7, Result<T8> op8, Result<T9> op9
	) {
		return () -> op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> op6.flatMap(
								v6 -> op7.flatMap(
									v7 -> op8.flatMap(
										v8 -> op9.flatMap(
											v9 -> ok(tuple(v1, v2, v3, v4, v5, v6, v7, v8, v9)
											))))))))));
	}

	interface Mapper1<T1> {
		Result<Tuple1<T1>> id();

		default <R> Result<R> map(FN1<R, T1> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN1<Result<R>, T1> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper2<T1, T2> {
		Result<Tuple2<T1, T2>> id();

		default <R> Result<R> map(FN2<R, T1, T2> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN2<Result<R>, T1, T2> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper3<T1, T2, T3> {
		Result<Tuple3<T1, T2, T3>> id();

		default <R> Result<R> map(FN3<R, T1, T2, T3> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN3<Result<R>, T1, T2, T3> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper4<T1, T2, T3, T4> {
		Result<Tuple4<T1, T2, T3, T4>> id();

		default <R> Result<R> map(FN4<R, T1, T2, T3, T4> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN4<Result<R>, T1, T2, T3, T4> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper5<T1, T2, T3, T4, T5> {
		Result<Tuple5<T1, T2, T3, T4, T5>> id();

		default <R> Result<R> map(FN5<R, T1, T2, T3, T4, T5> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN5<Result<R>, T1, T2, T3, T4, T5> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper6<T1, T2, T3, T4, T5, T6> {
		Result<Tuple6<T1, T2, T3, T4, T5, T6>> id();

		default <R> Result<R> map(FN6<R, T1, T2, T3, T4, T5, T6> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN6<Result<R>, T1, T2, T3, T4, T5, T6> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper7<T1, T2, T3, T4, T5, T6, T7> {
		Result<Tuple7<T1, T2, T3, T4, T5, T6, T7>> id();

		default <R> Result<R> map(FN7<R, T1, T2, T3, T4, T5, T6, T7> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN7<Result<R>, T1, T2, T3, T4, T5, T6, T7> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper8<T1, T2, T3, T4, T5, T6, T7, T8> {
		Result<Tuple8<T1, T2, T3, T4, T5, T6, T7, T8>> id();

		default <R> Result<R> map(FN8<R, T1, T2, T3, T4, T5, T6, T7, T8> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN8<Result<R>, T1, T2, T3, T4, T5, T6, T7, T8> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper9<T1, T2, T3, T4, T5, T6, T7, T8, T9> {
		Result<Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> id();

		default <R> Result<R> map(FN9<R, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN9<Result<R>, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}
}