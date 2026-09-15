# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# primes.pl -- numeric and associative-array workload for the Perl
# interpreter benchmark.  Drives integer arithmetic in the eval loop, array
# growth, hash insertion and lookup, and sprintf formatting.
#
# usage: perl-perl4.riscv primes.pl [limit] [numbers-to-factor]
#
# Perl 4, not Perl 5: no scalar(), and user subs are called as &f().
# The sieve is cheap; trial-division factoring in interpreted Perl is not, so
# the two are scaled separately -- see the Makefile for the tiny/small/ref
# combinations.

$limit = $ARGV[0] || 20000;
$fmax  = $ARGV[1] || $limit;      # factor the numbers up to here

# Sieve of Eratosthenes over a plain array.
@sieve = ();
for ($i = 2; $i <= $limit; $i++) { $sieve[$i] = 1; }
for ($i = 2; $i * $i <= $limit; $i++) {
    next unless $sieve[$i];
    for ($j = $i * $i; $j <= $limit; $j += $i) { $sieve[$j] = 0; }
}

@primes = ();
for ($i = 2; $i <= $limit; $i++) { push(@primes, $i) if $sieve[$i]; }

# Factor every number in range by trial division against the primes found:
# the inner loop is interpreter-bound, which is the point.
%factor_count = ();
$total_factors = 0;
for ($n = 2; $n <= $fmax; $n++) {
    $m = $n;
    $count = 0;
    foreach $p (@primes) {
        last if $p * $p > $m;
        while ($m % $p == 0) { $m /= $p; $count++; }
    }
    $count++ if $m > 1;
    $factor_count{$count}++;
    $total_factors += $count;
}

# Twin primes, via a hash lookup rather than an index scan.
%is_prime = ();
foreach $p (@primes) { $is_prime{$p} = 1; }
$twins = 0;
foreach $p (@primes) { $twins++ if $is_prime{$p + 2}; }

printf("primes: limit=%d factored=%d count=%d last=%d\n",
       $limit, $fmax, $#primes+1, $primes[$#primes]);
printf("primes: factors=%d twins=%d\n", $total_factors, $twins);
foreach $k (sort { $a <=> $b } keys %factor_count) {
    printf("primes: %d numbers have %d prime factors\n", $factor_count{$k}, $k);
}
print "primes: done\n";
