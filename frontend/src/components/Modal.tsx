import React from 'react';

type ModalProps = {
  onClose: () => void;
  children: React.ReactNode;
  title?: string;
};

const Modal: React.FC<ModalProps> = ({ onClose, children, title }) => {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()} role="dialog" aria-modal="true">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{title || 'Modal'}</h3>
          <button className="button" onClick={onClose} aria-label="Close">×</button>
        </div>
        <div style={{ marginTop: 8 }}>{children}</div>
      </div>
    </div>
  );
};

export default Modal;

