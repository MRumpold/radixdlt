/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.network.transport.udp;

import java.net.InetAddress;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * NAT handler for inbound packets.
 */
public interface NatHandler {

	/**
	 * Reset confirmed address.
	 */
	void reset();

	/**
	 * Handle an inbound packet to check if address checking/challenge is required.
	 * <p>
	 * Note that data will be consumed from {@code buf} as required to handle NAT processing.
	 *
	 * @param ctx channel context to write any required address challenges on
	 * @param peerAddress the address of the sending peer
	 * @param buf the inbound packet
	 */
	void handleInboundPacket(ChannelHandlerContext ctx, InetAddress peerAddress, ByteBuf buf);

	/**
	 * The caller needs to filter all packets with this method to catch validation UDP frames.
	 * <p>
	 * Note that no data will be consumed from {@code buf}.
	 *
	 * @param bytes packet previously sent by start validation.
	 * @return true when packet was part of the validation process(and can be ignored by the caller) false otherwise.
	 */
	boolean endValidation(ByteBuf buf);


	InetAddress getAddress();

}
