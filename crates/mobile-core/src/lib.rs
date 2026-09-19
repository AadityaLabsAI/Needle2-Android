//! Portable Android-facing capability model.

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Capability {
    WifiScan,
    MonitorMode,
    PacketCapture,
    PacketInjection,
    ExternalAdapter,
}

pub fn capability_name(capability: Capability) -> &'static str {
    match capability {
        Capability::WifiScan => "wifi_scan",
        Capability::MonitorMode => "monitor_mode",
        Capability::PacketCapture => "packet_capture",
        Capability::PacketInjection => "packet_injection",
        Capability::ExternalAdapter => "external_adapter",
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn names_are_stable() {
        assert_eq!(capability_name(Capability::WifiScan), "wifi_scan");
        assert_eq!(capability_name(Capability::MonitorMode), "monitor_mode");
        assert_eq!(capability_name(Capability::PacketCapture), "packet_capture");
        assert_eq!(capability_name(Capability::PacketInjection), "packet_injection");
        assert_eq!(capability_name(Capability::ExternalAdapter), "external_adapter");
    }
}
