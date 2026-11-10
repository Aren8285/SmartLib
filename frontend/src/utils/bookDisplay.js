const SUBJECT_TONES = {
  'Computer Science': 'book-cover--ink',
  Mathematics: 'book-cover--ochre',
  Physics: 'book-cover--forest',
  Literature: 'book-cover--clay',
  History: 'book-cover--plum',
  Economics: 'book-cover--teal',
};

export function coverTone(subject) {
  return SUBJECT_TONES[subject] || 'book-cover--ink';
}

export function coverMark(title) {
  return (title || '?')
    .replace(/^(the|a|an)\s+/i, '')
    .trim()
    .charAt(0)
    .toUpperCase();
}

export function subjectSlug(subject) {
  return (subject || '').toLowerCase().replace(/\s+/g, '-');
}