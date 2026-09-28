# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# anagram.pl -- string, hash and regex workload for the Perl interpreter benchmark.
#
# Reads a word list over HTIF (which is the point: it exercises the frontend
# server's file proxy the way a real workload does), groups words into anagram
# classes by sorting each word's letters, and runs a handful of regex passes
# over the list.
#
# usage: perl-perl4.riscv anagram.pl words.txt [repeats]
#
# Perl 4, not Perl 5: no scalar(@array), and reverse of a string needs an
# explicit scalar context.

$file = $ARGV[0] || 'words.txt';
$reps = $ARGV[1] || 4;

open(WORDS, $file) || die "cannot open $file: $!\n";
@words = ();
while (<WORDS>) {
    chop;
    next if /^\s*$/;
    push(@words, $_);
}
close(WORDS);

printf("anagram: read %d words from %s\n", $#words+1, $file);

# Anagram classes: sort the letters of each word to make a key.
%class = ();
for ($r = 0; $r < $reps; $r++) {
    %class = ();
    foreach $w (@words) {
        $key = join('', sort(split(//, $w)));
        $class{$key} .= "$w ";
    }
}

$biggest = 0;
$biggest_key = '';
$classes = 0;
foreach $k (keys %class) {
    $classes++;
    @members = split(' ', $class{$k});
    # Break ties on the key, not on hash iteration order: perl 4 and perl 5
    # walk a hash differently, and the run has to be reproducible across both
    # so that the target's output can be diffed against a host build.
    if (@members > $biggest ||
        (@members == $biggest && ($biggest_key eq '' || $k lt $biggest_key))) {
        $biggest = @members;
        $biggest_key = $k;
    }
}
printf("anagram: %d classes, largest has %d members (key %s)\n",
       $classes, $biggest, $biggest_key);

# Regex passes: the pattern compiler and the matcher are a large part of what
# an interpreter benchmark should measure.
$vowel_heavy = 0;
$doubled = 0;
$palindromic = 0;
for ($r = 0; $r < $reps; $r++) {
    $vowel_heavy = $doubled = $palindromic = 0;
    foreach $w (@words) {
        $vowel_heavy++ if $w =~ /[aeiou].*[aeiou].*[aeiou]/;
        $doubled++     if $w =~ /(.)\1/;
        $palindromic++ if $w eq scalar reverse($w);
    }
}
printf("anagram: vowel_heavy=%d doubled=%d palindromic=%d\n",
       $vowel_heavy, $doubled, $palindromic);

# Substitution and sprintf churn over the whole list.
$total = 0;
foreach $w (@words) {
    $s = $w;
    $s =~ s/([aeiou])/<$1>/g;
    $s = sprintf("%-24s %3d", $s, length($s));
    $total += length($s);
}
printf("anagram: formatted %d characters\n", $total);
print "anagram: done\n";
