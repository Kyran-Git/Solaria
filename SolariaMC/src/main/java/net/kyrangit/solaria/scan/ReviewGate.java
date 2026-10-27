package net.kyrangit.solaria.scan;

import net.kyrangit.solaria.blueprint.AstronomyClass;

/** Server-owned revision and consent gate. No world or network side effects. */
public final class ReviewGate {
    private int revision;
    private AstronomyClass label;
    private boolean accepted, teacher, consent;

    public ReviewGate(AstronomyClass label, boolean accepted) {
        this.label = label;
        this.accepted = accepted && label != null;
    }

    public boolean apply(int nextRevision, AstronomyClass nextLabel, boolean nextAccepted) {
        if (nextRevision <= revision || nextRevision > 20 || nextLabel == null) return false;
        // Consent is to a label, not to every future teacher correction.
        if (nextLabel != label || !nextAccepted) consent = false;
        revision = nextRevision;
        label = nextLabel;
        accepted = nextAccepted;
        teacher = true;
        return true;
    }

    public boolean agree(int responseRevision) {
        if (responseRevision != revision || !accepted) return false;
        consent = true;
        return true;
    }

    public int revision() { return revision; }
    public AstronomyClass label() { return label; }
    public boolean accepted() { return accepted; }
    public boolean consent() { return consent; }
    public boolean ready() { return teacher && accepted && consent; }
    public String application() { return !accepted ? "rejected" : ready() ? "ready" : "waiting_student"; }
}
