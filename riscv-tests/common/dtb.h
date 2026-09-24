#ifndef __DTB_H__
#define __DTB_H__

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "util.h"

#define ALIGN(p,x) (((p)+(x)-1) & ~(x-1))

const static uint32_t ntohl(uint32_t x) {
	uint8_t* p = (uint8_t*) &x;
	return (((uint32_t)p[0]) << 24) |
	       (((uint32_t)p[1]) << 16) |
	       (((uint32_t)p[2]) <<  8) |
	       (((uint32_t)p[3]));
}

const static uint32_t htonl(uint32_t x) { return ntohl(x); }

const static uint64_t ntohll(uint64_t x) {
	uint8_t* p = (uint8_t*) &x;
	return (((uint64_t)p[0]) << 56) |
	       (((uint64_t)p[1]) << 48) |
	       (((uint64_t)p[2]) << 40) |
	       (((uint64_t)p[3]) << 32) |
	       (((uint64_t)p[4]) << 24) |
	       (((uint64_t)p[5]) << 16) |
	       (((uint64_t)p[6]) <<  8) |
	       (((uint64_t)p[7]));
}

const static uint64_t htonll(uint64_t x) { return ntohll(x); }

/////////////////////
// FDT definitions

struct fdt_header {
	uint32_t magic;
	uint32_t totalsize;
	uint32_t off_dt_struct;
	uint32_t off_dt_strings;
	uint32_t off_mem_rsvmap;
	uint32_t version;
	uint32_t last_comp_version;
	uint32_t boot_cpuid_phys;
	uint32_t size_dt_strings;
	uint32_t size_dt_struct;
};

#define FDT_MAGIC       0xd00dfeed
#define FDT_TAGSIZE     sizeof(fdt32_t)

#define FDT_BEGIN_NODE  0x1
#define FDT_END_NODE    0x2
#define FDT_PROP        0x3
#define FDT_NOP         0x4
#define FDT_END         0x9

/////////////////////

extern void* dtb;

int get_mem_range_from_dtb(void** start,size_t* len) {
	uint32_t* p = dtb;
	if(*p != htonl(FDT_MAGIC)) return 0;
	
	debugf("found DTB at %p\n",p);
	
	struct fdt_header* header = (struct fdt_header*)p;
	
	char* strings = ((char*)p) + ntohl(header->off_dt_strings);
	
	p += ALIGN(ntohl(header->off_dt_struct),4)>>2;
	int level = 0;
	
	void* reg_prop;
	uint32_t reg_len;
	int is_mem = 0;
	
	while(1) {
		uint32_t tag = ntohl(*p++);
		//~ debugf("%06p: %08x\n",p,tag);
		switch(tag) {
		case FDT_BEGIN_NODE: {
			is_mem = 0;
			level++;
			p += (ALIGN(strlen((char*)p)+1,4)>>2);
			continue;
		}
		case FDT_PROP: {
			uint32_t len = ntohl(*p);
			char* name = strings + ntohl(p[1]);
			void* val = p+2;
			if(strcmp(name,"device_type") == 0)
				is_mem = strcmp(val,"memory") == 0;
			if(strcmp(name,"reg") == 0) { reg_prop = val; reg_len = len; }
			//~ printf("%p: prop \"%s\" len 0x%02x\n",p,name,len);
			p += (ALIGN(len,4)>>2)+2;
			continue;
		}
		case FDT_END_NODE:
			if(is_mem) {
				switch(reg_len) {
				case 8: {
					uint32_t* props = reg_prop;
					*start = (void*)(uint64_t)ntohl(props[0]);
					*len = ntohl(props[1]);
					debugf("found mem at %p len 0x%x\n",p,len);
					return 1;
				}
				case 16: {
					uint64_t* props = reg_prop;
					*start = (void*)ntohll(props[0]);
					*len = ntohll(props[1]);
					debugf("found mem at %p len 0x%lx\n",*start,*len);
					return 1;
				}
				default:
					printf("found mem at %p len 0x%lx\n",*start,*len);
				}
			}

			level--;
			if(!level) return 0;
			continue;
		case FDT_NOP:
			continue;
		default: // fail
			printf("unknown FDT token tag 0x%x\n",tag);
			exit(1);
		}
	}
}

#endif
