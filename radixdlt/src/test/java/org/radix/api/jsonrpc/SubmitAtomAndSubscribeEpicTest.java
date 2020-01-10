package org.radix.api.jsonrpc;

import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import org.everit.json.schema.Schema;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.InOrder;
import org.radix.api.services.AtomsService;
import org.radix.api.services.SingleAtomListener;
import com.radixdlt.constraintmachine.DataPointer;
import org.radix.validation.ConstraintMachineValidationException;
import com.radixdlt.serialization.Serialization;

import java.io.IOException;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class SubmitAtomAndSubscribeEpicTest {
	@Test
	public void testAtomValidationError() throws IOException {
		AtomsService atomsService = mock(AtomsService.class);
		Schema schema = mock(Schema.class);
		Serialization serializer = mock(Serialization.class);
		Consumer<JSONObject> callback = mock(Consumer.class);
		JSONObject action = mock(JSONObject.class);
		JSONObject params = mock(JSONObject.class);
		JSONObject jsonAtom = new JSONObject();
		when(action.getJSONObject("params")).thenReturn(params);
		when(params.getJSONObject("atom")).thenReturn(jsonAtom);

		Atom atom = mock(Atom.class);

		when(serializer.fromJsonObject(eq(jsonAtom), eq(Atom.class))).thenReturn(atom);

		doAnswer((invocation) -> {
			((SingleAtomListener) invocation.getArguments()[1]).onError(AID.ZERO, new ConstraintMachineValidationException(atom, "", DataPointer.ofAtom()));
			return null;
		}).when(atomsService).submitAtom(any(), any());

		SubmitAtomAndSubscribeEpic epic = new SubmitAtomAndSubscribeEpic(atomsService, schema, callback);
		epic.action(action);

		InOrder inOrder = inOrder(callback);
		inOrder.verify(callback, times(1)).accept(argThat(o ->
			o.has("method") && o.has("params")
				&& o.getString("method").equals("AtomSubmissionState.onNext")
				&& o.getJSONObject("params").getJSONObject("data").has("pointerToIssue")
		));
	}
}