package com.taportal.aria;

import com.taportal.domain.briefing.Briefing;
import com.taportal.domain.briefing.Briefing.Section;
import com.taportal.domain.briefing.Briefing.Why;

/**
 * A {@link Briefing} as plain text, in the shape both brains use: the records a model is given,
 * and the lines the scripted brain answers with. Placeholders stay in.
 */
final class BriefingText {

    private BriefingText() {
    }

    /** Everything in the briefing: the records a model answers from. */
    static String records(Briefing b) {
        StringBuilder s = new StringBuilder("About: ").append(b.about()).append("\n");
        s.append(sections(b));
        s.append("\nWhy things are as they are:\n");
        if (b.whys().isEmpty()) {
            s.append("- Nothing here is waiting, held back or not sent.\n");
        }
        for (Why w : b.whys()) {
            s.append("- ").append(why(w)).append("\n");
        }
        if (!b.gaps().isEmpty()) {
            s.append("\nNot in these records:\n");
            b.gaps().forEach(g -> s.append("- ").append(g).append("\n"));
        }
        if (!b.links().isEmpty()) {
            s.append("\nScreens the recruiter can open:\n");
            b.links().forEach(l -> s.append("- ").append(l.label()).append("\n"));
        }
        return s.toString().trim();
    }

    /** The sections: a title on its own line, then one "- " line per fact. */
    static String sections(Briefing b) {
        StringBuilder s = new StringBuilder();
        for (Section sec : b.sections()) {
            s.append("\n").append(sec.title()).append("\n");
            sec.lines().forEach(l -> s.append("- ").append(l).append("\n"));
        }
        return s.toString();
    }

    /** "What, because why. Next step." */
    static String why(Why w) {
        String line = w.what() + ", because " + w.because() + ".";
        return w.next() == null ? line : line + " " + w.next();
    }
}
