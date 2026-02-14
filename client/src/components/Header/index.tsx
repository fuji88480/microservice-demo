import { Link } from 'react-router-dom';
import { Button } from '../ui/button';
import { useContext } from 'react';
import { AuthContext } from '@/auth/auth-context';

export default function Header() {
  const { user, isLoading } = useContext(AuthContext);

  return (
    <div className="bg-white border-b">
      <div className="h-14 flex items-center justify-between px-6 sm:px-8">
        <div>
          <Link to="/" className="flex items-center gap-2">
            <img src="/logo.jpeg" alt="logo" className="h-8" />
            <span className="font-mono font-semibold">GitTix</span>
          </Link>
        </div>

        <div className="gap-4 font-mono">
          {isLoading && <span className="text-sm">Loading...</span>}

          {!isLoading && !user && (
            <Link to="/auth/signin">
              <Button size="sm" variant="outline">
                ログイン／新規登録
              </Button>
            </Link>
          )}

          {!isLoading && user && <span>{user?.email}</span>}
        </div>
      </div>
    </div>
  );
}
