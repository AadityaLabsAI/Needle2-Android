//! Portable security-audit domain model shared by Android and other frontends.

use serde::{Deserialize, Serialize};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum Capability { WifiScan, MonitorMode, PacketCapture, PacketInjection, ExternalAdapter, UsbHost }

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct DeviceCapability {
    pub capability: Capability,
    pub supported: bool,
    pub reason: String,
}

pub fn capability_name(capability: Capability) -> &'static str {
    match capability {
        Capability::WifiScan => "wifi_scan",
        Capability::MonitorMode => "monitor_mode",
        Capability::PacketCapture => "packet_capture",
        Capability::PacketInjection => "packet_injection",
        Capability::ExternalAdapter => "external_adapter",
        Capability::UsbHost => "usb_host",
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct AccessPoint {
    pub ssid: String,
    pub bssid: String,
    pub frequency_mhz: i32,
    pub signal_dbm: i32,
    pub security: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct AuditFinding {
    pub severity: String,
    pub title: String,
    pub detail: String,
}

pub fn audit_findings(ap: &AccessPoint) -> Vec<AuditFinding> {
    let mut findings = Vec::new();
    let sec = ap.security.to_ascii_uppercase();
    if sec.contains("WEP") {
        findings.push(AuditFinding { severity: "high".into(), title: "Legacy WEP security".into(), detail: "WEP is obsolete and should be replaced.".into() });
    } else if sec.contains("OPEN") || sec.is_empty() {
        findings.push(AuditFinding { severity: "high".into(), title: "No encryption detected".into(), detail: "Verify whether an open network is intentionally exposed.".into() });
    } else if sec.contains("WPA2") && !sec.contains("WPA3") {
        findings.push(AuditFinding { severity: "info".into(), title: "WPA2 network".into(), detail: "WPA2 is still widely deployed; review configuration and transition plans.".into() });
    } else {
        findings.push(AuditFinding { severity: "ok".into(), title: "Modern security advertised".into(), detail: "The scan advertises a protected network.".into() });
    }
    findings
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn names_are_stable() {
        assert_eq!(capability_name(Capability::WifiScan), "wifi_scan");
        assert_eq!(capability_name(Capability::UsbHost), "usb_host");
    }
    #[test]
    fn open_network_is_flagged() {
        let ap=AccessPoint{ssid:"x".into(),bssid:"00:00:00:00:00:00".into(),frequency_mhz:2412,signal_dbm:-50,security:"".into()};
        assert_eq!(audit_findings(&ap)[0].severity,"high");
    }
}
