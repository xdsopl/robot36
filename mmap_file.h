#ifndef MMAP_FILE_H
#define MMAP_FILE_H
int mmap_file_ro(void **, char *, size_t *);
int mmap_file_rw(void **, char *, size_t);
int munmap_file(void *, size_t);
#endif
