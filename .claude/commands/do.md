Look up and execute a command from the Knowledge Foundation.

The first word of `$ARGUMENTS` is the command name; anything after it
is that command's own arguments.

## Instructions

1. Resolve the foundation path from the `KNOWLEDGE_FOUNDATION_PATH`
   env var. If unset, stop and tell the user to set it in their
   shell profile (e.g.,
   `export KNOWLEDGE_FOUNDATION_PATH=~/Development/GitHub/knowledge-foundation`).

2. Split `$ARGUMENTS` at the first whitespace: the head is `<command>`,
   the tail (possibly empty) is `<args>`.

3. Read `$KNOWLEDGE_FOUNDATION_PATH/knowledge/meta/commands/<command>/_index.md`.

4. Execute the instructions in the body of that file, with `<args>`
   available to them as the command's arguments.

If the command is not found, list available commands by globbing
`$KNOWLEDGE_FOUNDATION_PATH/knowledge/meta/commands/*/_index.md` and
reading each one's `summary` frontmatter field.
