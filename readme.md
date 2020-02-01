# Robin Hood Hashing

A robin hood style hash table implementation. See [1] for an explanation of the name.

[1]: http://codecapsule.com/2013/11/11/robin-hood-hashing/


## Overview

This is a hash table implementation built on top of an array of items using open addressing and linear probing without tombstones.

Each position of the table is occupied by either a _hole_ or an _entry_ holding a key and value. The size of the array is defined through the current `shift` of the hash table as `2 ** shift + shift`. `shift` starts at 0 when an empty hash table is created and increases in steps of 1 when the table is grown.

Each possible key `k` has an _ideal position_ `ipos(k)` defined as `mod(hash(key), (2 ** shift))`. For each entry stored in the array, all of the following conditions hold:

- The _displacement_ of an entry as position `pos` in the array, defined as `pos - ipos(k)`, is non-negative but smaller than `shift`.
- Unless the displacement is 0, the entry must be preceded by another entry.
- If two entries are stored next to each other in the array, the first entry's ideal position must be smaller or equal to the second entry's ideal position.


## Operations

### Find

Finding the entry with a specific key `k` works as follows. Start at the ideal position `ipos(k)` of the key, linearly searching forwards. Terminate the search when one of the following conditions is met:

- The current position `pos` is equal to `ipos(k) + shift`.
- The current item is a hole.
- The current item is an entry with a key whose ideal position is larger than the ideal position of `k`.
- The current item is the entry with key `k`.

All but the last case imply that the hash table does not contain an entry with this key.


### Remove

Removing an entry with a specific key starts by finding its position `pos`, as described above. If no entry with the requested key is found, nothing needs to be done.

Once the position `pos` of the entry is known, removing the entry works as follows. Look at position `pos + 1`. If its an entry which is not in its ideal position, copy it to `pos` and repeat the removal process with position `pos + 1`. Otherwise, overwrite position `pos` with a hole.


### Insert

Inserting a new entry `e` with key `k` works as follows. Start at position `ipos(k)`, linearly searching forward and trying to find a position, where the entry can be inserted. The search stops at position `pos` when one of the following conditions is met:

- The position is occupied by a hole or an already existing entry with key `k`. In this case, overwrite the item with the new entry. The operation is completed.
- The position is occupied by an entry with an ideal position which is greater than `ipos(k)`. In this case, swap the entry at position `pos` with `e` and continue the search at `pos + 1` with the new `e`.

The search also stops when the current position `pos` is equal to `ipos(k) + shift`. In this case, grow the table as described below and then try to insert the entry again.


### Grow

Growing the table is possible at any time, but in the implementation in this repository, it only happens when inserting an element fails because all possible positions for an entry are already occupied.

Growing a table that currently has size `old_size` works as follows. Increase `shift` (usually by one) and resize the table according to the definition above, filling any new positions with holes. Then, process all items from `old_size - 1` to the start of the table in reverse order.

For each entry encountered, whose new ideal position, according to the new value for `shift`, is greater than its current position, do the following. Remove it from the table as described above, skipping the instructions necessary to determine the position of the entry. Then, insert the entry as described above.
