package org.graphiks.kadre.consumer;

import org.graphiks.kadre.diagnostics.KadreFailure;
import org.graphiks.kadre.diagnostics.KadreResourceKind;
import org.graphiks.kadre.diagnostics.KadreResult;
import org.graphiks.kadre.policy.KadrePolicies;
import org.graphiks.kadre.policy.KadrePolicy;

public final class Consumer {
    private Consumer() {}

    public static KadrePolicy defaultPolicy() {
        return KadrePolicies.INSTANCE.getDefault();
    }

    public static KadrePolicy realtimePolicy() {
        return KadrePolicies.INSTANCE.getRealtime();
    }

    public static KadrePolicy recordingPolicy() {
        return KadrePolicies.INSTANCE.getRecording();
    }

    public static KadreResult<String> success(String value) {
        return new KadreResult.Success<>(value);
    }

    public static KadreFailure closedHost() {
        return new KadreFailure.Closed(KadreResourceKind.Host);
    }
}
