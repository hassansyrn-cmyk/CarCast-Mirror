# CarCast Mirror versioning policy

The Play beta line uses a user-facing semantic version and a monotonically increasing integer version code. The current Play-beta branch is `versionName = 0.9.1-beta` and `versionCode = 3`. The rollback baseline remains `v0.9.0-beta1` with version code `2`.

Every Play upload must increase `versionCode`, even when the user-facing version changes only in its suffix. Do not reuse a version code from a previous internal, closed, or production track. Before building a release, compare the next code with the highest code visible in Play Console. A later candidate may use `0.9.1-beta.2` with code `4`, or a stable release may use `1.0.0` with a larger code after the product owner approves the stable milestone.

The branch name `release/play-beta` and the tag `v0.9.1-play-beta` are separate from the version code. Do not create the tag until physical regression passes and the signed AAB has been reviewed.
