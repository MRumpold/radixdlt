package org.radix.api.jsonrpc;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.radixdlt.common.AID;
import org.everit.json.schema.Schema;
import org.json.JSONObject;
import org.radix.api.services.AtomsService;
import org.radix.api.services.SingleAtomListener;
import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.atoms.particles.conflict.ParticleConflictException;
import org.radix.exceptions.AtomAlreadyStoredException;
import org.radix.exceptions.ValidationException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.validation.ConstraintMachineValidationException;

/**
 * Epic responsible for translating an atom submission JSON RPC request to the response and resulting
 * notifications.
 *
 * TODO: utilize a framework like akka to deal with asynchronicity
 */
public class SubmitAtomAndSubscribeEpic {

	/**
	 * States an atom submission can be in
	 */
	public enum AtomSubmissionState {
		SUBMITTING, SUBMITTED, STORED, VALIDATION_ERROR, UNKNOWN_ERROR, COLLISION, UNSUITABLE_PEER
	}

	private static final Logger LOGGER = Logging.getLogger("SubmitAndSubscribeEpic");

	/**
	 * Interface for atom submission and return of results
	 */
	private final AtomsService atomsService;

	/**
	 * Json Schema which an atom will be validated against
	 */
	private final Schema atomSchema;

	/**
	 * Stream of JSON RPC objects to be sent back in the same channel
	 */
	private final Consumer<JSONObject> callback;

	public SubmitAtomAndSubscribeEpic(AtomsService atomsService, Schema atomSchema, Consumer<JSONObject> callback) {
		this.atomsService = atomsService;
		this.atomSchema = atomSchema;
		this.callback = callback;
	}

	public void action(JSONObject jsonMethod) {
		Object id = jsonMethod.get("id");
		JSONObject params = jsonMethod.getJSONObject("params");
		Object subscriberId = jsonMethod.getJSONObject("params").get("subscriberId");
		JSONObject jsonAtom = params.getJSONObject("atom");


		final BiConsumer<AtomSubmissionState, JSONObject> sendAtomSubmissionState = (state, data) -> {
			JSONObject responseParams = new JSONObject();
			responseParams.put("subscriberId", subscriberId);
			responseParams.put("value", state.toString());
			if (data != null) {
				responseParams.put("data", data);
			}
			JSONObject notification = new JSONObject();
			notification.put("jsonrpc", "2.0");
			notification.put("method", "AtomSubmissionState.onNext");
			notification.put("params", responseParams);
			callback.accept(notification);
		};

		try {
			atomSchema.validate(jsonAtom);
		} catch (org.everit.json.schema.ValidationException e) {
			callback.accept(JsonRpcUtil.errorResponse(id, -32000, "Invalid atom", e.toJSON()));
			return;
		}

		SingleAtomListener subscriber = new SingleAtomListener() {
			@Override
			public void onStored(boolean first) {
				JSONObject data = new JSONObject();
				data.put("justStored", first);

				sendAtomSubmissionState.accept(AtomSubmissionState.STORED, data);
			}

			@Override
			public void onError(AID atomId, Throwable e) {
				if (e instanceof ParticleConflictException) {
					ParticleConflictException particleConflictException = (ParticleConflictException) e;
					ParticleConflict conflict = particleConflictException.getConflict();
					JSONObject data = new JSONObject();
					data.put("pointerToIssue", conflict.getDataPointer().toString());
					data.put("message",
							conflict.getAtomIds().stream().filter(a -> !a.equals(atomId)).findAny().map(Object::toString).orElse(null));

					sendAtomSubmissionState.accept(AtomSubmissionState.COLLISION, data);
				} else if (e instanceof ValidationException) {
					ValidationException validationException = (ValidationException) e;

					String pointerToIssue = null;
					if (validationException instanceof ConstraintMachineValidationException) {
						ConstraintMachineValidationException cmException = (ConstraintMachineValidationException) validationException;
						pointerToIssue = cmException.getPointerToIssue();
					}

					JSONObject data = new JSONObject();
					data.put("message", e.getMessage());

					if (pointerToIssue != null) {
						data.put("pointerToIssue", pointerToIssue);
					}

					sendAtomSubmissionState.accept(AtomSubmissionState.VALIDATION_ERROR, data);
				} else if (e instanceof AtomAlreadyStoredException) {
					JSONObject data = new JSONObject();
					data.put("justStored", false);

					sendAtomSubmissionState.accept(AtomSubmissionState.STORED, data);
				} else {
					JSONObject data = new JSONObject();
					data.put("message", e.getMessage());

					sendAtomSubmissionState.accept(AtomSubmissionState.UNKNOWN_ERROR, data);
				}
			}
		};

		try {
			atomsService.submitAtom(jsonAtom, subscriber);
		} catch (IllegalArgumentException e) {
			callback.accept(JsonRpcUtil.errorResponse(id, -32000, e.getMessage()));
		}
	}
}
