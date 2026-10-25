package org.backblue.edit;

import org.backblue.config.Config;
import org.backblue.extension.LiveFramework;
import org.backblue.extension.SelfEditable;

public class EditRulebook implements SelfEditable, LiveFramework {

    @Override
    public Scope scope() {
        return Scope.whole(Config.Rulebook_JSON);
    }

}
