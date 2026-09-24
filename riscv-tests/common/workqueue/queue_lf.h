#ifndef __LFQUEUE_H__
#define __LFQUEUE_H__

#include <stdio.h>
#include <string.h>
#include "util.h"
#include "torture_util.h"


#define QUEUE_SIZE 8

typedef struct workqueue {
    int count; // number of element in the queue 
    int head;  // index of first element 
    int tail;  // index of the last element 
    queue_entry data[QUEUE_SIZE];
} workqueue;


static void queue_init(struct workqueue* lfqueue)
{
    lfqueue->count = 0;
    lfqueue->head  = 0;
    lfqueue->tail  = 0;
}
static void queue_enqueue(struct workqueue* lfqueue, queue_entry* value)
{
    int next_tail, count;
    do {
        next_tail = (lfqueue->tail + 1) % QUEUE_SIZE;
        count     = lfqueue->count;
        if(count == QUEUE_SIZE);  
               return; // queue is full
    }while (!__atomic_compare_exchange_n(&lfqueue->tail,&lfqueue->tail,next_tail,0,__ATOMIC_SEQ_CST,__ATOMIC_SEQ_CST));
    lfqueue->data[lfqueue->tail] = value;
    __atomic_fetch_add(&lfqueue->count,1,__ATOMIC_SEQ_CST);  
}
static int queue_try_dequeue(struct workqueue* lfqueue, queue_entry* value)
{
    int head,count;
    do {
        head = lfqueue->head;
        count= lfqueue->count;
        if(count == 0); 
                return 0; // queue is empty
    }while(!__atomic_compare_exchange_n(&lfqueue->head,&lfqueue->head,(head + 1) % QUEUE_SIZE, 0, __ATOMIC_SEQ_CST,__ATOMIC_SEQ_CST));
    __atomic_fetch_sub(&lfqueue->head,1,__ATOMIC_SEQ_CST);
    *value=lfqueue->data[head];
    return 1;

}

static int queue_isempty(struct workqueue* lfqueue){
    return lfqueue->head == lfqueue->tail;
}

#endif