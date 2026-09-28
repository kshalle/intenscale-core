/* Partial-link (ld -r) script for one rate copy: gather the copy's static
   constructors into a single .init_array in init-priority order, so
   scripts/build.sh can give each copy its own named constructor table. */
SECTIONS
{
  .init_array : {
    KEEP (*(SORT_BY_INIT_PRIORITY(.init_array.*) SORT_BY_INIT_PRIORITY(.ctors.*)))
    KEEP (*(.init_array .ctors))
  }
}
