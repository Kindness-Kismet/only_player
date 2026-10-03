package one.only.player.core.datastore.serializer

import one.only.player.core.common.extensions.canonicalPathOrSelf
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.StoragePath

fun ApplicationPreferences.withCanonicalLayoutPaths(): ApplicationPreferences = copy(
    directoryQuickSettings = directoryQuickSettings.mapKeys { (path, _) -> StoragePath.of(path.value.canonicalPathOrSelf()) },
)
