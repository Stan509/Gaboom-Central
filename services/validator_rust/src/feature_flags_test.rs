#[cfg(test)]
mod tests {
    use crate::feature_flags::FeatureFlags;

    #[test]
    fn test_feature_flags_disabled_by_default() {
        let flags = vec![
            "OFFLINE_V2",
            "SYNC_ENGINE_V2",
            "QUEUE_ENGINE",
            "SQLCIPHER",
            "GO_GATEWAY",
            "RUST_SIGNATURE",
        ];

        for flag in flags {
            assert_eq!(FeatureFlags::is_enabled(flag), false);
        }
    }
}
