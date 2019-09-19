
package dev.ukanth.ufirewall;

public class InterfaceDetails {
	// firewall policy
	public boolean isRoaming = false;
	public boolean isTethered = false;
	public boolean tetherStatusKnown = false;
	public String lanMaskV4 = "";
	public String lanMaskV6 = "";
	// TODO: identify DNS servers instead of opening up port 53/udp to all LAN hosts

	// supplementary info
	String wifiName = "";
	boolean netEnabled = false;
	boolean noIP = false;
	public int netType = -1;

	public boolean equals(InterfaceDetails that) {
		if (this.isRoaming != that.isRoaming ||
			this.isTethered != that.isTethered ||
			this.tetherStatusKnown != that.tetherStatusKnown ||
			!this.lanMaskV4.equals(that.lanMaskV4) ||
			!this.lanMaskV6.equals(that.lanMaskV6) ||
			!this.wifiName.equals(that.wifiName) ||
			this.netEnabled != that.netEnabled ||
			this.netType != that.netType ||
			this.noIP != that.noIP)
			return false;
		return true;
	}
}