/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.application.tokens.construction;

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.application.tokens.state.TokensInAccount;

public final class BurnTokenConstructor implements ActionConstructor<BurnToken> {

	@Override
	public void construct(BurnToken burnToken, TxBuilder txBuilder) throws TxBuilderException {
		txBuilder.deallocateFungible(
			TokensInAccount.class,
			p -> p.getResourceAddr().equals(burnToken.resourceAddr())
				&& p.getHoldingAddr().equals(burnToken.from()),
			amt -> new TokensInAccount(burnToken.from(), amt, burnToken.resourceAddr()),
			burnToken.amount(),
			() -> new TxBuilderException("Not enough balance to for fee burn.")
		);
		txBuilder.end();
	}
}
