package org.radix.network.discovery;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;

public class Whitelist
{
	private static final Logger networkLog = Logging.getLogger ("network");

	private Set<String> parameters = new HashSet<String>();

	public Whitelist(String parameters)
	{
		if (parameters == null)
			return;

		String[] split = parameters.split(",");

		for (String parameter : split)
		{
			if (parameter.trim().length() == 0)
				continue;

			this.parameters.add(parameter.trim());
		}
	}

	private int[] convert(String host)
	{
		String[] segments;
		int[] output;

		// IPV4 //
		if (host.contains("."))
		{
			output = new int[4];
			segments = host.split("\\.");
		}
		// IPV6 //
		else if (host.contains(":"))
		{
			output = new int[8];
			segments = host.split(":");
		}
		else
			return new int[] {0, 0, 0, 0};

		Arrays.fill(output, Integer.MAX_VALUE);
		for (int s = 0 ; s < segments.length ; s++)
		{
			if (segments[s].equalsIgnoreCase("*"))
				break;

			output[s] = Integer.valueOf(segments[s]);
		}

		return output;
	}

	private boolean isRange(String parameter)
	{
		if (parameter.contains("-"))
			return true;

		return false;
	}

	private boolean isInRange(String parameter, String address)
	{
		String[] hosts = parameter.split("-");

		if (hosts.length != 2)
			throw new IllegalStateException("Range is invalid");

		int[] target = convert(address);
		int[] low = convert(hosts[0]);
		int[] high = convert(hosts[1]);

		if (low.length != high.length || target.length != low.length)
			return false;

		for (int s = 0 ; s < low.length ; s++)
		{
			if (low[s] < high[s])
			{
				int[] swap = low;
				low = high;
				high = swap;
				break;
			}

			if (target[s] < low[s] || target[s] > high[s])
				return false;
		}

		return true;
	}

	private boolean isMask(String parameter)
	{
		if (parameter.contains("*") || parameter.contains("::"))
			return true;

		return false;
	}

	private boolean isMasked(String parameter, String address)
	{
		int[] target = convert(address);
		int[] mask = convert(parameter);

		if (target.length != mask.length)
			return false;

		for (int s = 0 ; s < mask.length ; s++)
		{
			if (mask[s] == Integer.MAX_VALUE)
				return true;
			else if (target[s] != mask[s])
				return false;
		}

		return false;
	}

	public boolean isWhitelisted(String hostName) {
		if (parameters.isEmpty()) {
			return true;
		}

		try {
			String hostAddress = InetAddress.getByName(hostName).getHostAddress();
			for (String parameter : parameters) {
				if (parameter.equalsIgnoreCase(hostName)
					|| isRange(parameter) && isInRange(parameter, hostAddress)
					|| isMask(parameter) && isMasked(parameter, hostAddress)) {
					return true;
				}
			}
		} catch (UnknownHostException ex) {
			networkLog.error(ex);
		}

		return false;
	}

	public static Whitelist from(RuntimeProperties properties) {
		return new Whitelist(Modules.get(RuntimeProperties.class).get("network.whitelist", ""));
	}
}
