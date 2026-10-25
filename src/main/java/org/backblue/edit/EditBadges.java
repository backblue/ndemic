package org.backblue.edit;

import net.dv8tion.jda.api.components.container.Container;
import org.backblue.config.Config;
import org.backblue.extension.LiveFramework;
import org.backblue.extension.SelfEditable;

public class EditBadges implements SelfEditable, LiveFramework {

    @Override
    public Scope scope() {
        return Scope.whole(Config.Badges_JSON);
    }

    public Container badges() {
        return null;
    }

}
