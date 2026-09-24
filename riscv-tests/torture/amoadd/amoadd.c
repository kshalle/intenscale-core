// See README_AMO.md for more info

#include <stdio.h>
#include "util.h"
#include "torture_util.h"

static uint64_t seed = 0x123456789abcdef;

// 256 prime numbers
// These are generated from /usr/games/primes, which is part of the
// bsdgames debian package.
unsigned int primes[256] = {
	900001, 900007, 900019, 900037, 900061, 900089, 900091, 900103,
	900121, 900139, 900143, 900149, 900157, 900161, 900169, 900187,
	900217, 900233, 900241, 900253, 900259, 900283, 900287, 900293,
	900307, 900329, 900331, 900349, 900397, 900409, 900443, 900461,
	900481, 900491, 900511, 900539, 900551, 900553, 900563, 900569,
	900577, 900583, 900587, 900589, 900593, 900607, 900623, 900649,
	900659, 900671, 900673, 900689, 900701, 900719, 900737, 900743,
	900751, 900761, 900763, 900773, 900797, 900803, 900817, 900821,
	900863, 900869, 900917, 900929, 900931, 900937, 900959, 900971,
	900973, 900997, 901007, 901009, 901013, 901063, 901067, 901079,
	901093, 901097, 901111, 901133, 901141, 901169, 901171, 901177,
	901183, 901193, 901207, 901211, 901213, 901247, 901249, 901253,
	901273, 901279, 901309, 901333, 901339, 901367, 901399, 901403,
	901423, 901427, 901429, 901441, 901447, 901451, 901457, 901471,
	901489, 901499, 901501, 901513, 901517, 901529, 901547, 901567,
	901591, 901613, 901643, 901657, 901679, 901687, 901709, 901717,
	901739, 901741, 901751, 901781, 901787, 901811, 901819, 901841,
	901861, 901891, 901907, 901909, 901919, 901931, 901937, 901963,
	901973, 901993, 901997, 902009, 902017, 902029, 902039, 902047,
	902053, 902087, 902089, 902119, 902137, 902141, 902179, 902191,
	902201, 902227, 902261, 902263, 902281, 902299, 902303, 902311,
	902333, 902347, 902351, 902357, 902389, 902401, 902413, 902437,
	902449, 902471, 902477, 902483, 902501, 902507, 902521, 902563,
	902569, 902579, 902591, 902597, 902599, 902611, 902639, 902653,
	902659, 902669, 902677, 902687, 902719, 902723, 902753, 902761,
	902767, 902771, 902777, 902789, 902807, 902821, 902827, 902849,
	902873, 902903, 902933, 902953, 902963, 902971, 902977, 902981,
	902987, 903017, 903029, 903037, 903073, 903079, 903103, 903109,
	903143, 903151, 903163, 903179, 903197, 903211, 903223, 903251,
	903257, 903269, 903311, 903323, 903337, 903347, 903359, 903367,
	903389, 903391, 903403, 903407, 903421, 903443, 903449, 903451,
	903457, 903479, 903493, 903527, 903541, 903547, 903563, 903569
};

// Given a random seed, returns a 64-bit number with the values 0-15
// in its 16 4-bit fields, in random order.
uint64_t line_permutation(uint32_t seed) {
	int a[16];
	
	permute(seed,16,a);
	
	uint64_t out = 0;
	for(int i=15; i>=0; i--) out |= (uint64_t)a[i] << (i*4);
	return out;
}


static volatile uint64_t counter = 0;

volatile cache_line accumulator_table[16];
static volatile int done[TOTAL_HARTS];
static volatile uint64_t hart_totals[TOTAL_HARTS];

static uint64_t next_line_map;
// a dest var to write to, to prevent gcc from optimizing out reads
volatile uint64_t dumpvar;

void init() {
	uint64_t trnd = seed + 10000;
	next_line_map = line_permutation(trnd);
	printf("amoadd: running on %d threads for %d iterations\n",TOTAL_HARTS,DEFAULT_ITERATIONS);
	printf("amoadd: next_line_map = %016lx, seed = %016lx\n",next_line_map,seed);
}

int thread_entry(int cid, int nc) {
	int hartId = read_csr(mhartid);
	uint64_t tacc;

	if(hartId >= TOTAL_HARTS) {
		printf("hart %d should not be here! bailing.\n",hartId);
		return 0;
	}
	
	if(hartId == 0) {
		init();
		
		init_done = 1;
	} else while(!init_done);
	
	uint64_t myprime = primes[hartId];
	int rnd = lfsr2(seed + hartId * 0xd933c10e5f7671c6);
	
	//~ printf("hart %d starting\n",hartId);

	for(int i=0; i<DEFAULT_ITERATIONS; i++) {
		int line = rnd & 0xf;
		AMOADD(accumulator_table[line][0],myprime);
		//~ accumulator_table[line][0] += myprime;
		int nextline = (next_line_map >> (line*4)) & 0xf;
		AMOADD(accumulator_table[nextline][1],myprime);
		//~ accumulator_table[nextline][1] += myprime;
		delay_cycles((rnd >> 4) & 0x07);

		// cause a read of another cache line
		nextline = (next_line_map >> (nextline*4)) & 0xf;
		tacc += accumulator_table[nextline][2];
		
		rnd = lfsr2(rnd);
	}
	dumpvar = tacc;
	done[hartId] = 1;
	
	//~ printf("hart %d done\n",hartId);
	if(hartId!=0) return 0;
	
	// hart 0 to check everything
	fence();
	for(int i=0;i<TOTAL_HARTS;i++) while(!done[i]);
	fence();
	debugf("accumulator table dump:\n");
	int ok = 1;

	uint64_t primes_total = 0, lines_total = 0;
	for(int i=0;i<TOTAL_HARTS;i++) primes_total += primes[i];
	for(int i=0;i<16;i++) {
		debugf("%16lx %16lx\n",accumulator_table[i][0],accumulator_table[i][1]);
		int nextline = (next_line_map >> (i*4)) & 0xf;
		if(accumulator_table[i][0] != accumulator_table[nextline][1]) {
			debugf("row %2d != row %2d!\n",i,nextline);
			ok = 0;
		}
		lines_total += accumulator_table[i][0];
	}
	if(lines_total != primes_total * DEFAULT_ITERATIONS) ok = 0;
	
	if(!ok) printf("FAIL\n");
	return !ok;
}
