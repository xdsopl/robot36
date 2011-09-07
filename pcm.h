
#ifndef PCM_H
#define PCM_H

typedef struct pcm {
	void (*close)(struct pcm *);
	void (*info)(struct pcm *);
	int (*rate)(struct pcm *);
	int (*channels)(struct pcm *);
	int (*read)(struct pcm *, short *, int);
} pcm_t;

void close_pcm(pcm_t *);
void info_pcm(pcm_t *);
int rate_pcm(pcm_t *);
int channels_pcm(pcm_t *);
int read_pcm(pcm_t *, short *, int);
int open_pcm(pcm_t **, char *);

#endif

