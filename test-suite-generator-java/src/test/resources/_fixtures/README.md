# Schema fixtures

Local copies of external JSON schemas used by the generator tests. They are
served by `MockSchemaServerService` (see `buildSrc/`) so that test builds do
not depend on remote services such as `schemastore.org`.

## Files

| File | Source | License |
|------|--------|---------|
| `base.json` | https://www.schemastore.org/base.json | Apache-2.0 (SchemaStore) |

## Updating

If a schema needs to be refreshed (e.g. new definitions are required), download
the latest version from the upstream URL and replace the file in this directory.
Keep the upstream URL recorded in the table above.
